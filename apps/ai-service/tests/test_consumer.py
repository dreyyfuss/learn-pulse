import asyncio
import json
import pytest
from unittest.mock import AsyncMock, MagicMock, patch

from app.kafka.consumer import CoursePublishedConsumer
from app.schemas.course import CoursePublishedEvent
from tests.conftest import VALID_COURSE_EVENT


def make_msg(payload, offset: int = 0) -> MagicMock:
    msg = MagicMock()
    msg.value = json.dumps(payload).encode("utf-8")
    msg.offset = offset
    return msg


@pytest.fixture
def mock_indexer():
    indexer = MagicMock()
    indexer.index.return_value = 5
    return indexer


@pytest.fixture
def consumer(mock_indexer):
    return CoursePublishedConsumer(indexer=mock_indexer)


class TestHandle:
    async def test_valid_event_calls_indexer(self, consumer, mock_indexer):
        await consumer._handle(make_msg(VALID_COURSE_EVENT))
        mock_indexer.index.assert_called_once()

    async def test_valid_event_passes_correct_course_id(self, consumer, mock_indexer):
        await consumer._handle(make_msg(VALID_COURSE_EVENT))
        event: CoursePublishedEvent = mock_indexer.index.call_args.args[0]
        assert event.courseId == VALID_COURSE_EVENT["courseId"]

    async def test_valid_event_parses_all_lessons(self, consumer, mock_indexer):
        await consumer._handle(make_msg(VALID_COURSE_EVENT))
        event: CoursePublishedEvent = mock_indexer.index.call_args.args[0]
        assert len(event.lessons) == len(VALID_COURSE_EVENT["lessons"])

    async def test_string_value_is_handled(self, consumer, mock_indexer):
        msg = MagicMock()
        msg.value = json.dumps(VALID_COURSE_EVENT)  # str, not bytes
        msg.offset = 0
        await consumer._handle(msg)
        mock_indexer.index.assert_called_once()

    async def test_invalid_json_does_not_raise(self, consumer, mock_indexer):
        msg = MagicMock()
        msg.value = b"this is not json"
        msg.offset = 0
        await consumer._handle(msg)
        mock_indexer.index.assert_not_called()

    async def test_invalid_schema_does_not_raise(self, consumer, mock_indexer):
        await consumer._handle(make_msg({"unexpected": "structure"}))
        mock_indexer.index.assert_not_called()

    async def test_empty_json_object_does_not_raise(self, consumer, mock_indexer):
        await consumer._handle(make_msg({}))
        mock_indexer.index.assert_not_called()

    async def test_missing_lessons_does_not_raise(self, consumer, mock_indexer):
        payload = {**VALID_COURSE_EVENT}
        del payload["lessons"]
        await consumer._handle(make_msg(payload))
        mock_indexer.index.assert_not_called()


class TestLifecycle:
    async def test_start_creates_and_starts_kafka_consumer(self, consumer):
        with patch("app.kafka.consumer.AIOKafkaConsumer") as MockKafka:
            mock_kafka = AsyncMock()
            # Make iteration immediately stop so the consume loop exits cleanly
            mock_kafka.__aiter__ = MagicMock(return_value=aiter([]))
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
        # Use a real asyncio task — MagicMock can't be used in 'await'
        async def forever():
            await asyncio.sleep(100)

        task = asyncio.create_task(forever())
        consumer._task = task

        mock_kafka = AsyncMock()
        consumer._consumer = mock_kafka

        await consumer.stop()

        assert task.cancelled()
        mock_kafka.stop.assert_awaited_once()

    async def test_stop_is_safe_when_not_started(self, consumer):
        # Should not raise even with no task or consumer
        await consumer.stop()


# ---------------------------------------------------------------------------
# Helper: synchronous iterator wrapped as async iter (no aiokafka needed)
# ---------------------------------------------------------------------------
class aiter:  # noqa: N801
    def __init__(self, items):
        self._iter = iter(items)

    def __aiter__(self):
        return self

    async def __anext__(self):
        try:
            return next(self._iter)
        except StopIteration:
            raise StopAsyncIteration
