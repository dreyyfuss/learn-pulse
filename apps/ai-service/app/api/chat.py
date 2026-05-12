import asyncio
import json
import logging
from typing import Annotated

import openai
from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from app.dependencies.infrastructure import get_user_id
from app.dependencies.services import get_chat_service
from app.exceptions import ForbiddenError, SessionNotFoundError
from app.schemas.chat import MessageRequest, SessionResponse
from app.services.chat_service import ChatService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/ai", tags=["ai"])


@router.get("/stream-test")
async def stream_test() -> StreamingResponse:
    """Diagnostic: streams 10 tokens, one per second, with no auth required."""
    async def gen():
        for i in range(10):
            yield f"data: {json.dumps({'token': f'token-{i} '})}\n\n"
            await asyncio.sleep(0.3)
        yield "data: [DONE]\n\n"

    return StreamingResponse(
        gen(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.post("/courses/{course_id}/sessions", response_model=SessionResponse, status_code=201)
async def create_session(
    course_id: str,
    user_id: Annotated[str, Depends(get_user_id)],
    service: Annotated[ChatService, Depends(get_chat_service)],
) -> SessionResponse:
    course_title = await service.verify_and_get_course(user_id, course_id)
    session_id = await service.create_session(user_id, course_id, course_title)
    return SessionResponse(sessionId=session_id)


@router.post("/courses/{course_id}/sessions/{session_id}/messages")
async def send_message(
    course_id: str,
    session_id: str,
    body: MessageRequest,
    user_id: Annotated[str, Depends(get_user_id)],
    service: Annotated[ChatService, Depends(get_chat_service)],
) -> StreamingResponse:
    async def event_stream():
        try:
            async for token in service.stream_message(session_id, user_id, body.message):
                yield f"data: {json.dumps({'token': token})}\n\n"
        except SessionNotFoundError:
            yield f"data: {json.dumps({'error': 'Session not found'})}\n\n"
        except ForbiddenError:
            yield f"data: {json.dumps({'error': 'Forbidden'})}\n\n"
        except openai.RateLimitError:
            logger.warning("Cerebras rate limit hit sessionId=%s", session_id)
            yield f"data: {json.dumps({'error': 'The AI is busy right now — please try again in a moment.'})}\n\n"
        except Exception:
            logger.exception("Streaming error sessionId=%s", session_id)
            yield f"data: {json.dumps({'error': 'Internal error'})}\n\n"
        finally:
            yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )
