import json
import pytest
from unittest.mock import AsyncMock, MagicMock

import httpx

from app.exceptions import EnrolmentError, ForbiddenError, SessionNotFoundError
from app.services.chat_service import ChatService


@pytest.fixture
def mock_redis():
    redis = AsyncMock()
    redis.get.return_value = None
    return redis


@pytest.fixture
def mock_pipeline():
    return MagicMock()


@pytest.fixture
def mock_http():
    return AsyncMock()


@pytest.fixture
def service(mock_redis, mock_pipeline, mock_http):
    return ChatService(redis=mock_redis, pipeline=mock_pipeline, http=mock_http)


def make_ok_response(course_title: str = "Python Basics") -> MagicMock:
    resp = MagicMock()
    resp.status_code = 200
    resp.json.return_value = {"enrolled": True, "started": True, "courseTitle": course_title}
    return resp


def make_session(user_id: str = "user-1", course_id: str = "c1", title: str = "Python Basics") -> str:
    return json.dumps({
        "userId": user_id,
        "courseId": course_id,
        "courseTitle": title,
        "messages": [],
    })


class TestVerifyAndGetCourse:
    async def test_returns_course_title_on_success(self, service, mock_http):
        mock_http.get.return_value = make_ok_response("Intro to Rust")
        result = await service.verify_and_get_course("user-1", "course-1")
        assert result == "Intro to Rust"

    async def test_sends_service_auth_header(self, service, mock_http):
        mock_http.get.return_value = make_ok_response()
        await service.verify_and_get_course("user-1", "course-1")
        headers = mock_http.get.call_args.kwargs["headers"]
        assert "X-Service-Auth" in headers

    async def test_raises_enrolment_error_on_403(self, service, mock_http):
        resp = MagicMock()
        resp.status_code = 403
        mock_http.get.return_value = resp
        with pytest.raises(EnrolmentError):
            await service.verify_and_get_course("user-1", "course-1")

    async def test_raises_enrolment_error_on_unexpected_status(self, service, mock_http):
        resp = MagicMock()
        resp.status_code = 500
        mock_http.get.return_value = resp
        with pytest.raises(EnrolmentError):
            await service.verify_and_get_course("user-1", "course-1")

    async def test_raises_enrolment_error_on_not_enrolled(self, service, mock_http):
        resp = MagicMock()
        resp.status_code = 200
        resp.json.return_value = {"enrolled": False, "started": False, "courseTitle": "T"}
        mock_http.get.return_value = resp
        with pytest.raises(EnrolmentError):
            await service.verify_and_get_course("user-1", "course-1")

    async def test_raises_enrolment_error_on_not_started(self, service, mock_http):
        resp = MagicMock()
        resp.status_code = 200
        resp.json.return_value = {"enrolled": True, "started": False, "courseTitle": "T"}
        mock_http.get.return_value = resp
        with pytest.raises(EnrolmentError):
            await service.verify_and_get_course("user-1", "course-1")

    async def test_raises_enrolment_error_on_request_error(self, service, mock_http):
        mock_http.get.side_effect = httpx.RequestError("connection refused")
        with pytest.raises(EnrolmentError, match="unavailable"):
            await service.verify_and_get_course("user-1", "course-1")


class TestCreateSession:
    async def test_returns_uuid_string(self, service, mock_redis):
        session_id = await service.create_session("user-1", "course-1", "Python Basics")
        assert isinstance(session_id, str)
        assert len(session_id) == 36  # UUID format

    async def test_stores_session_in_redis(self, service, mock_redis):
        session_id = await service.create_session("user-1", "course-1", "Python Basics")
        mock_redis.set.assert_awaited_once()
        key = mock_redis.set.call_args.args[0]
        assert key == f"chat:{session_id}"

    async def test_stores_correct_session_payload(self, service, mock_redis):
        await service.create_session("user-42", "course-7", "Rust Basics")
        raw = mock_redis.set.call_args.args[1]
        session = json.loads(raw)
        assert session["userId"] == "user-42"
        assert session["courseId"] == "course-7"
        assert session["courseTitle"] == "Rust Basics"
        assert session["messages"] == []

    async def test_sets_ttl_on_redis_key(self, service, mock_redis):
        await service.create_session("u", "c", "T")
        call_kwargs = mock_redis.set.call_args.kwargs
        assert "ex" in call_kwargs
        assert call_kwargs["ex"] > 0

    async def test_each_session_has_unique_id(self, service, mock_redis):
        id1 = await service.create_session("u", "c", "T")
        id2 = await service.create_session("u", "c", "T")
        assert id1 != id2


class TestStreamMessage:
    async def test_raises_session_not_found_when_redis_returns_none(self, service, mock_redis):
        mock_redis.get.return_value = None
        with pytest.raises(SessionNotFoundError):
            async for _ in service.stream_message("s1", "user-1", "hello"):
                pass

    async def test_raises_forbidden_when_user_id_mismatch(self, service, mock_redis):
        mock_redis.get.return_value = make_session(user_id="user-2")
        with pytest.raises(ForbiddenError):
            async for _ in service.stream_message("s1", "user-1", "hello"):
                pass

    async def test_yields_tokens_from_pipeline(self, service, mock_redis, mock_pipeline):
        mock_redis.get.return_value = make_session(user_id="user-1")

        async def fake_stream(query, course_id, course_title, history):
            yield "Hello"
            yield " world"

        mock_pipeline.stream = fake_stream

        tokens = [t async for t in service.stream_message("s1", "user-1", "question")]
        assert tokens == ["Hello", " world"]

    async def test_appends_messages_to_history_in_redis(self, service, mock_redis, mock_pipeline):
        mock_redis.get.return_value = make_session(user_id="user-1")

        async def fake_stream(query, course_id, course_title, history):
            yield "answer"

        mock_pipeline.stream = fake_stream

        async for _ in service.stream_message("s1", "user-1", "my question"):
            pass

        mock_redis.set.assert_awaited_once()
        stored = json.loads(mock_redis.set.call_args.args[1])
        messages = stored["messages"]
        assert messages[-2] == {"role": "user", "content": "my question"}
        assert messages[-1] == {"role": "assistant", "content": "answer"}

    async def test_passes_history_window_to_pipeline(self, service, mock_redis, mock_pipeline):
        session = {
            "userId": "user-1",
            "courseId": "c1",
            "courseTitle": "T",
            "messages": [
                {"role": "user", "content": f"q{i}"}
                for i in range(20)  # 20 messages, window is 10
            ],
        }
        mock_redis.get.return_value = json.dumps(session)

        captured_history: list = []

        async def fake_stream(query, course_id, course_title, history):
            captured_history.extend(history)
            yield "ok"

        mock_pipeline.stream = fake_stream

        async for _ in service.stream_message("s1", "user-1", "q"):
            pass

        assert len(captured_history) == 10  # only last 10

    async def test_passes_correct_course_context_to_pipeline(self, service, mock_redis, mock_pipeline):
        mock_redis.get.return_value = make_session(
            user_id="user-1", course_id="course-xyz", title="Advanced Python"
        )
        captured: dict = {}

        async def fake_stream(query, course_id, course_title, history):
            captured["course_id"] = course_id
            captured["course_title"] = course_title
            yield "ok"

        mock_pipeline.stream = fake_stream

        async for _ in service.stream_message("s1", "user-1", "q"):
            pass

        assert captured["course_id"] == "course-xyz"
        assert captured["course_title"] == "Advanced Python"

    async def test_updates_redis_with_extended_ttl(self, service, mock_redis, mock_pipeline):
        mock_redis.get.return_value = make_session(user_id="user-1")

        async def fake_stream(query, course_id, course_title, history):
            yield "token"

        mock_pipeline.stream = fake_stream

        async for _ in service.stream_message("s1", "user-1", "q"):
            pass

        call_kwargs = mock_redis.set.call_args.kwargs
        assert "ex" in call_kwargs
