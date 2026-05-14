"""
FastAPI endpoint tests.

Uses a test-only app instance (no lifespan / real infrastructure) with
dependency_overrides to inject a mock ChatService. This follows the
FastAPI best practice of testing routes in isolation from infrastructure.
"""

import json
import pytest
from unittest.mock import AsyncMock, MagicMock
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from httpx import ASGITransport, AsyncClient

from app.api.chat import router as chat_router
from app.dependencies.infrastructure import get_user_id
from app.dependencies.services import get_chat_service
from app.exceptions import EnrolmentError, ForbiddenError, SessionNotFoundError
from app.middleware.logging import RequestIDMiddleware


# ---------------------------------------------------------------------------
# Test app factory — identical routes/handlers/middleware but no lifespan
# so no real infrastructure (Redis, ChromaDB, Kafka) is started in tests.
# ---------------------------------------------------------------------------

def build_test_app() -> FastAPI:
    app = FastAPI()
    app.add_middleware(RequestIDMiddleware)
    app.include_router(chat_router)

    @app.exception_handler(EnrolmentError)
    async def enrolment_handler(request: Request, exc: EnrolmentError) -> JSONResponse:
        return JSONResponse(status_code=403, content={"detail": str(exc)})

    @app.get("/healthz")
    async def health():
        return {"status": "ok"}

    return app


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def mock_service() -> MagicMock:
    service = MagicMock()
    service.verify_and_get_course = AsyncMock(return_value="Python Basics")
    service.create_session = AsyncMock(return_value="test-session-uuid")
    return service


@pytest.fixture
async def client(mock_service) -> AsyncClient:
    app = build_test_app()
    app.dependency_overrides[get_chat_service] = lambda: mock_service
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac


@pytest.fixture
async def authed_client(mock_service) -> AsyncClient:
    """Client pre-wired with X-User-Id; also overrides get_user_id for convenience."""
    app = build_test_app()
    app.dependency_overrides[get_chat_service] = lambda: mock_service
    app.dependency_overrides[get_user_id] = lambda: "user-1"
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

class TestHealthz:
    async def test_returns_200(self, client):
        resp = await client.get("/healthz")
        assert resp.status_code == 200

    async def test_returns_ok_body(self, client):
        resp = await client.get("/healthz")
        assert resp.json() == {"status": "ok"}


# ---------------------------------------------------------------------------
# Middleware
# ---------------------------------------------------------------------------

class TestRequestIDMiddleware:
    async def test_response_includes_request_id(self, client):
        resp = await client.get("/healthz")
        assert "x-request-id" in resp.headers

    async def test_propagates_existing_request_id(self, client):
        resp = await client.get("/healthz", headers={"X-Request-ID": "my-trace-id"})
        assert resp.headers["x-request-id"] == "my-trace-id"

    async def test_generates_new_id_when_not_provided(self, client):
        resp = await client.get("/healthz")
        rid = resp.headers["x-request-id"]
        assert rid != "" and rid != "-"


# ---------------------------------------------------------------------------
# GET /api/ai/stream-test
# ---------------------------------------------------------------------------

class TestStreamTest:
    async def test_returns_200(self, client):
        resp = await client.get("/api/ai/stream-test")
        assert resp.status_code == 200

    async def test_content_type_is_event_stream(self, client):
        resp = await client.get("/api/ai/stream-test")
        assert "text/event-stream" in resp.headers["content-type"]

    async def test_body_contains_tokens(self, client):
        resp = await client.get("/api/ai/stream-test")
        assert "token-0" in resp.text

    async def test_body_ends_with_done(self, client):
        resp = await client.get("/api/ai/stream-test")
        assert "[DONE]" in resp.text


# ---------------------------------------------------------------------------
# POST /api/ai/courses/{course_id}/sessions
# ---------------------------------------------------------------------------

class TestCreateSession:
    async def test_missing_user_id_header_returns_401(self, client):
        resp = await client.post("/api/ai/courses/c1/sessions")
        assert resp.status_code == 401

    async def test_returns_201_on_success(self, client):
        resp = await client.post(
            "/api/ai/courses/c1/sessions",
            headers={"X-User-Id": "user-1"},
        )
        assert resp.status_code == 201

    async def test_returns_session_id_in_body(self, client):
        resp = await client.post(
            "/api/ai/courses/c1/sessions",
            headers={"X-User-Id": "user-1"},
        )
        assert resp.json() == {"sessionId": "test-session-uuid"}

    async def test_calls_verify_with_correct_args(self, client, mock_service):
        await client.post(
            "/api/ai/courses/course-xyz/sessions",
            headers={"X-User-Id": "user-42"},
        )
        mock_service.verify_and_get_course.assert_awaited_once_with("user-42", "course-xyz")

    async def test_calls_create_session_with_course_title(self, client, mock_service):
        mock_service.verify_and_get_course.return_value = "Advanced Rust"
        await client.post(
            "/api/ai/courses/c1/sessions",
            headers={"X-User-Id": "user-1"},
        )
        mock_service.create_session.assert_awaited_once_with("user-1", "c1", "Advanced Rust")

    async def test_enrolment_error_returns_403(self, client, mock_service):
        mock_service.verify_and_get_course.side_effect = EnrolmentError("not enrolled")
        resp = await client.post(
            "/api/ai/courses/c1/sessions",
            headers={"X-User-Id": "user-1"},
        )
        assert resp.status_code == 403

    async def test_enrolment_error_body_contains_detail(self, client, mock_service):
        mock_service.verify_and_get_course.side_effect = EnrolmentError("not enrolled")
        resp = await client.post(
            "/api/ai/courses/c1/sessions",
            headers={"X-User-Id": "user-1"},
        )
        assert resp.json()["detail"] == "not enrolled"


# ---------------------------------------------------------------------------
# POST /api/ai/courses/{course_id}/sessions/{session_id}/messages
# ---------------------------------------------------------------------------

class TestSendMessage:
    async def test_missing_user_id_returns_401(self, client):
        resp = await client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hello"},
        )
        assert resp.status_code == 401

    async def test_returns_200_with_valid_request(self, authed_client, mock_service):
        async def fake_stream(session_id, user_id, message):
            yield "token"

        mock_service.stream_message = fake_stream
        resp = await authed_client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hello"},
        )
        assert resp.status_code == 200

    async def test_content_type_is_event_stream(self, authed_client, mock_service):
        async def fake_stream(session_id, user_id, message):
            yield "token"

        mock_service.stream_message = fake_stream
        resp = await authed_client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hello"},
        )
        assert "text/event-stream" in resp.headers["content-type"]

    async def test_streams_tokens_as_sse(self, authed_client, mock_service):
        async def fake_stream(session_id, user_id, message):
            yield "Hello"
            yield " world"

        mock_service.stream_message = fake_stream
        resp = await authed_client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hi"},
        )
        assert json.dumps({"token": "Hello"}) in resp.text
        assert json.dumps({"token": " world"}) in resp.text

    async def test_stream_ends_with_done_frame(self, authed_client, mock_service):
        async def fake_stream(session_id, user_id, message):
            yield "token"

        mock_service.stream_message = fake_stream
        resp = await authed_client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hi"},
        )
        assert "data: [DONE]" in resp.text

    async def test_session_not_found_yields_sse_error(self, authed_client, mock_service):
        async def error_stream(session_id, user_id, message):
            raise SessionNotFoundError("not found")
            yield  # make it an async generator

        mock_service.stream_message = error_stream
        resp = await authed_client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hi"},
        )
        assert resp.status_code == 200  # SSE errors don't change HTTP status
        assert "Session not found" in resp.text

    async def test_forbidden_error_yields_sse_error(self, authed_client, mock_service):
        async def error_stream(session_id, user_id, message):
            raise ForbiddenError()
            yield

        mock_service.stream_message = error_stream
        resp = await authed_client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hi"},
        )
        assert "Forbidden" in resp.text

    async def test_rate_limit_error_yields_friendly_sse_message(self, authed_client, mock_service):
        import openai

        async def error_stream(session_id, user_id, message):
            raise openai.RateLimitError(
                "rate limited",
                response=MagicMock(status_code=429, headers={}),
                body={},
            )
            yield

        mock_service.stream_message = error_stream
        resp = await authed_client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hi"},
        )
        assert "busy" in resp.text.lower() or "try again" in resp.text.lower()

    async def test_generic_exception_yields_internal_error_sse(self, authed_client, mock_service):
        async def error_stream(session_id, user_id, message):
            raise RuntimeError("something exploded")
            yield

        mock_service.stream_message = error_stream
        resp = await authed_client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hi"},
        )
        assert "Internal error" in resp.text

    async def test_done_frame_always_sent_even_on_error(self, authed_client, mock_service):
        async def error_stream(session_id, user_id, message):
            raise RuntimeError("boom")
            yield

        mock_service.stream_message = error_stream
        resp = await authed_client.post(
            "/api/ai/courses/c1/sessions/s1/messages",
            json={"message": "hi"},
        )
        assert "data: [DONE]" in resp.text

    async def test_passes_session_id_and_user_id_to_service(self, authed_client, mock_service):
        received: dict = {}

        async def capture_stream(session_id, user_id, message):
            received["session_id"] = session_id
            received["user_id"] = user_id
            yield "ok"

        mock_service.stream_message = capture_stream
        await authed_client.post(
            "/api/ai/courses/course-1/sessions/session-abc/messages",
            json={"message": "hi"},
        )
        assert received["session_id"] == "session-abc"
        assert received["user_id"] == "user-1"
