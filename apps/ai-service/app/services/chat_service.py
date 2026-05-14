import json
import logging
import uuid
from collections.abc import AsyncIterator

import httpx
from redis.asyncio import Redis

from app.config.settings import settings
from app.exceptions import EnrolmentError, ForbiddenError, SessionNotFoundError
from app.rag.pipeline import RagPipeline

logger = logging.getLogger(__name__)


class ChatService:
    def __init__(self, redis: Redis, pipeline: RagPipeline, http: httpx.AsyncClient) -> None:
        self._redis = redis
        self._pipeline = pipeline
        self._http = http

    async def verify_and_get_course(self, user_id: str, course_id: str) -> str:
        """Calls course-service to verify enrolment + started status.

        Returns the course title on success; raises EnrolmentError otherwise.
        """
        try:
            resp = await self._http.get(
                f"{settings.course_service_url}/internal/courses/{course_id}/enrolment",
                params={"userId": user_id},
                headers={"X-Service-Auth": settings.service_auth_secret},
                timeout=5.0,
            )
        except httpx.RequestError as exc:
            logger.error("Could not reach course-service: %s", exc)
            raise EnrolmentError("Course service unavailable")

        if resp.status_code == 403:
            raise EnrolmentError("Not enrolled or course not started")
        if resp.status_code != 200:
            raise EnrolmentError(f"Unexpected response from course-service: {resp.status_code}")

        data = resp.json()
        if not data.get("enrolled") or not data.get("started"):
            raise EnrolmentError("Not enrolled or course not started")

        return data["courseTitle"]

    async def create_session(self, user_id: str, course_id: str, course_title: str) -> str:
        session_id = str(uuid.uuid4())
        session = {
            "userId": user_id,
            "courseId": course_id,
            "courseTitle": course_title,
            "messages": [],
        }
        await self._redis.set(
            f"chat:{session_id}",
            json.dumps(session),
            ex=settings.chat_session_ttl_seconds,
        )
        logger.info(
            "Session created sessionId=%s userId=%s courseId=%s",
            session_id, user_id, course_id,
        )
        return session_id

    async def stream_message(
        self,
        session_id: str,
        user_id: str,
        message: str,
    ) -> AsyncIterator[str]:
        raw = await self._redis.get(f"chat:{session_id}")
        if raw is None:
            raise SessionNotFoundError(session_id)

        session = json.loads(raw)
        if session["userId"] != user_id:
            raise ForbiddenError()

        history = session["messages"][-settings.chat_history_window:]
        full_reply: list[str] = []

        async for token in self._pipeline.stream(
            query=message,
            course_id=session["courseId"],
            course_title=session["courseTitle"],
            history=history,
        ):
            full_reply.append(token)
            yield token

        session["messages"].append({"role": "user", "content": message})
        session["messages"].append({"role": "assistant", "content": "".join(full_reply)})
        await self._redis.set(
            f"chat:{session_id}",
            json.dumps(session),
            ex=settings.chat_session_ttl_seconds,
        )
