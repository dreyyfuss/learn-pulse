import asyncio
import json
import pytest
from unittest.mock import AsyncMock, MagicMock, patch

from app.kafka.generation_consumer import CourseGenerationConsumer
from app.kafka.producer import GenerationEventProducer
from app.config.settings import settings


# ── Shared test data ──────────────────────────────────────────────────────────

VALID_REQUESTED_EVENT = {
    "eventId": "evt-001",
    "eventType": "course.generation.requested",
    "version": 1,
    "occurredAt": "2024-01-15T10:00:00Z",
    "jobId": "job-123",
    "instructorId": "inst-42",
    "prompt": "Create a Python course for beginners",
}

VALID_COMPLETED_PAYLOAD = {
    "eventId": "evt-002",
    "eventType": "course.generation.completed",
    "version": 1,
    "occurredAt": "2024-01-15T10:01:00Z",
    "jobId": "job-123",
    "instructorId": "inst-42",
    "course": {
        "title": "Python Basics",
        "description": "Learn Python",
        "category": "Programming",
        "modules": [],
    },
}


def make_msg(payload, offset: int = 0) -> MagicMock:
    msg = MagicMock()
    msg.value = json.dumps(payload).encode("utf-8")
    msg.offset = offset
    msg.headers = []
    return msg


# ── Fixtures ──────────────────────────────────────────────────────────────────

@pytest.fixture
def mock_pipeline():
    pipeline = AsyncMock()
    pipeline.generate = AsyncMock(return_value=VALID_COMPLETED_PAYLOAD)
    return pipeline


@pytest.fixture
def mock_producer():
    return AsyncMock()


@pytest.fixture
def consumer(mock_pipeline, mock_producer):
    return CourseGenerationConsumer(pipeline=mock_pipeline, producer=mock_producer)


# ── TestHandle ────────────────────────────────────────────────────────────────

class TestHandle:
    async def test_valid_event_calls_pipeline(self, consumer, mock_pipeline):
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))
        mock_pipeline.generate.assert_awaited_once()

    async def test_passes_correct_job_id_to_pipeline(self, consumer, mock_pipeline):
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))
        event = mock_pipeline.generate.call_args.args[0]
        assert event.jobId == VALID_REQUESTED_EVENT["jobId"]

    async def test_passes_correct_prompt_to_pipeline(self, consumer, mock_pipeline):
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))
        event = mock_pipeline.generate.call_args.args[0]
        assert event.prompt == VALID_REQUESTED_EVENT["prompt"]

    async def test_valid_event_publishes_completed(self, consumer, mock_producer):
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))
        mock_producer.publish_completed.assert_awaited_once_with(VALID_COMPLETED_PAYLOAD)

    async def test_string_value_is_handled(self, consumer, mock_pipeline):
        msg = MagicMock()
        msg.value = json.dumps(VALID_REQUESTED_EVENT)  # str, not bytes
        msg.offset = 0
        msg.headers = []
        await consumer._handle(msg)
        mock_pipeline.generate.assert_awaited_once()

    async def test_invalid_json_does_not_raise(self, consumer, mock_pipeline):
        msg = MagicMock()
        msg.value = b"not valid json"
        msg.offset = 0
        msg.headers = []
        await consumer._handle(msg)
        mock_pipeline.generate.assert_not_awaited()

    async def test_invalid_schema_does_not_raise(self, consumer, mock_pipeline):
        await consumer._handle(make_msg({"unexpected": "fields"}))
        mock_pipeline.generate.assert_not_awaited()

    async def test_empty_payload_does_not_raise(self, consumer, mock_pipeline):
        await consumer._handle(make_msg({}))
        mock_pipeline.generate.assert_not_awaited()

    async def test_pipeline_failure_publishes_failed(self, consumer, mock_pipeline, mock_producer):
        mock_pipeline.generate.side_effect = RuntimeError("LLM unavailable")
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))
        mock_producer.publish_failed.assert_awaited_once()

    async def test_pipeline_failure_passes_job_id(self, consumer, mock_pipeline, mock_producer):
        mock_pipeline.generate.side_effect = RuntimeError("LLM unavailable")
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))
        job_id = mock_producer.publish_failed.call_args.args[0]
        assert job_id == VALID_REQUESTED_EVENT["jobId"]

    async def test_pipeline_failure_passes_instructor_id(self, consumer, mock_pipeline, mock_producer):
        mock_pipeline.generate.side_effect = RuntimeError("LLM unavailable")
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))
        instructor_id = mock_producer.publish_failed.call_args.args[1]
        assert instructor_id == VALID_REQUESTED_EVENT["instructorId"]

    async def test_pipeline_failure_passes_error_message_as_reason(self, consumer, mock_pipeline, mock_producer):
        mock_pipeline.generate.side_effect = RuntimeError("quota exceeded")
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))
        reason = mock_producer.publish_failed.call_args.args[2]
        assert "quota exceeded" in reason

    async def test_pipeline_failure_does_not_raise(self, consumer, mock_pipeline):
        mock_pipeline.generate.side_effect = RuntimeError("LLM unavailable")
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))  # must not raise

    async def test_pipeline_failure_does_not_publish_completed(self, consumer, mock_pipeline, mock_producer):
        mock_pipeline.generate.side_effect = RuntimeError("LLM unavailable")
        await consumer._handle(make_msg(VALID_REQUESTED_EVENT))
        mock_producer.publish_completed.assert_not_awaited()


# ── TestLifecycle ─────────────────────────────────────────────────────────────

class _aiter:
    """Minimal async iterator for lifecycle tests."""
    def __init__(self, items):
        self._iter = iter(items)

    def __aiter__(self):
        return self

    async def __anext__(self):
        try:
            return next(self._iter)
        except StopIteration:
            raise StopAsyncIteration


class TestLifecycle:
    async def test_start_creates_and_starts_kafka_consumer(self, consumer):
        with patch("app.kafka.generation_consumer.AIOKafkaConsumer") as MockKafka:
            mock_kafka = AsyncMock()
            mock_kafka.__aiter__ = MagicMock(return_value=_aiter([]))
            MockKafka.return_value = mock_kafka

            await consumer.start()
            MockKafka.assert_called_once()
            mock_kafka.start.assert_awaited_once()
            assert consumer._task is not None
            consumer._task.cancel()
            try:
                await consumer._task
            except asyncio.CancelledError:
                pass

    async def test_stop_cancels_task_and_stops_kafka(self, consumer):
        async def forever():
            await asyncio.sleep(100)

        task = asyncio.create_task(forever())
        consumer._task = task
        consumer._consumer = AsyncMock()

        await consumer.stop()

        assert task.cancelled()
        consumer._consumer.stop.assert_awaited_once()

    async def test_stop_is_safe_when_not_started(self, consumer):
        await consumer.stop()  # must not raise


# ── TestGenerationEventProducer ───────────────────────────────────────────────

@pytest.fixture
def mock_kafka_producer():
    prod = AsyncMock()
    prod.send_and_wait = AsyncMock()
    return prod


@pytest.fixture
def producer(mock_kafka_producer):
    p = GenerationEventProducer()
    p._producer = mock_kafka_producer
    return p


class TestGenerationEventProducer:
    async def test_publish_completed_sends_to_correct_topic(self, producer, mock_kafka_producer):
        await producer.publish_completed(VALID_COMPLETED_PAYLOAD)
        topic = mock_kafka_producer.send_and_wait.call_args.args[0]
        assert topic == settings.kafka_topic_course_generation_completed

    async def test_publish_completed_sends_json_bytes(self, producer, mock_kafka_producer):
        await producer.publish_completed(VALID_COMPLETED_PAYLOAD)
        value = mock_kafka_producer.send_and_wait.call_args.kwargs["value"]
        assert isinstance(value, bytes)
        assert json.loads(value) == VALID_COMPLETED_PAYLOAD

    async def test_publish_failed_sends_to_correct_topic(self, producer, mock_kafka_producer):
        await producer.publish_failed("job-1", "inst-1", "timeout")
        topic = mock_kafka_producer.send_and_wait.call_args.args[0]
        assert topic == settings.kafka_topic_course_generation_failed

    async def test_publish_failed_includes_job_id(self, producer, mock_kafka_producer):
        await producer.publish_failed("job-xyz", "inst-1", "timeout")
        value = mock_kafka_producer.send_and_wait.call_args.kwargs["value"]
        assert json.loads(value)["jobId"] == "job-xyz"

    async def test_publish_failed_includes_instructor_id(self, producer, mock_kafka_producer):
        await producer.publish_failed("job-1", "inst-abc", "timeout")
        value = mock_kafka_producer.send_and_wait.call_args.kwargs["value"]
        assert json.loads(value)["instructorId"] == "inst-abc"

    async def test_publish_failed_includes_reason(self, producer, mock_kafka_producer):
        await producer.publish_failed("job-1", "inst-1", "API rate limited")
        value = mock_kafka_producer.send_and_wait.call_args.kwargs["value"]
        assert json.loads(value)["reason"] == "API rate limited"

    async def test_publish_failed_event_type_is_correct(self, producer, mock_kafka_producer):
        await producer.publish_failed("job-1", "inst-1", "error")
        value = mock_kafka_producer.send_and_wait.call_args.kwargs["value"]
        assert json.loads(value)["eventType"] == "course.generation.failed"

    async def test_publish_failed_has_valid_event_id(self, producer, mock_kafka_producer):
        import uuid
        await producer.publish_failed("job-1", "inst-1", "error")
        value = mock_kafka_producer.send_and_wait.call_args.kwargs["value"]
        uuid.UUID(json.loads(value)["eventId"])  # raises if not a valid UUID

    async def test_publish_failed_has_occurred_at(self, producer, mock_kafka_producer):
        from datetime import datetime
        await producer.publish_failed("job-1", "inst-1", "error")
        value = mock_kafka_producer.send_and_wait.call_args.kwargs["value"]
        datetime.fromisoformat(json.loads(value)["occurredAt"])  # raises if not ISO format
