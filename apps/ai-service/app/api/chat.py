from fastapi import APIRouter, Request

from app.schemas.chat import ChatRequest, ChatResponse
from app.services import chat_service

router = APIRouter(prefix="/api/ai", tags=["ai"])


@router.get("/healthz")
async def health():
    return {"status": "ok"}


@router.post("/courses/{course_id}/chat", response_model=ChatResponse)
async def chat_endpoint(course_id: str, body: ChatRequest, request: Request):
    pipeline = request.app.state.pipeline
    reply, cached = await chat_service.chat(pipeline, course_id, body.message)
    return ChatResponse(reply=reply, cached=cached)
