import asyncio
import json
import logging

from aiokafka import AIOKafkaConsumer
from pydantic import ValidationError

from app.config.settings import settings
from app.rag.indexer import CourseIndexer
from app.schemas.course import CoursePublishedEvent

logger = logging.getLogger(__name__)


class CoursePublishedConsumer:
    def __init__(self, indexer: CourseIndexer) -> None:
        self._indexer = indexer
        self._consumer: AIOKafkaConsumer | None = None
        self._task: asyncio.Task | None = None

    async def start(self) -> None:
        self._consumer = AIOKafkaConsumer(
            settings.kafka_topic_course_published,
            bootstrap_servers=settings.kafka_bootstrap_servers,
            group_id=settings.kafka_group_id,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
        )
        await self._consumer.start()
        self._task = asyncio.create_task(self._consume())
        logger.info(
            "CoursePublishedConsumer started topic=%s group=%s",
            settings.kafka_topic_course_published,
            settings.kafka_group_id,
        )

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        if self._consumer:
            await self._consumer.stop()
        logger.info("CoursePublishedConsumer stopped")

    async def _consume(self) -> None:
        async for msg in self._consumer:
            try:
                await self._handle(msg)
                await self._consumer.commit()
            except Exception:
                logger.exception(
                    "Unhandled error processing message offset=%s", msg.offset
                )

    async def _handle(self, msg) -> None:
        raw = msg.value.decode("utf-8") if isinstance(msg.value, bytes) else msg.value
        try:
            event = CoursePublishedEvent.model_validate(json.loads(raw))
        except (json.JSONDecodeError, ValidationError):
            logger.exception("Invalid course.published payload offset=%s", msg.offset)
            return

        logger.info(
            "course.published received courseId=%s title=%r instructor=%r lessons=%d",
            event.courseId,
            event.title,
            event.instructor.fullName,
            len(event.lessons),
        )

        # Embedding is CPU-bound; run off the event loop to keep it unblocked
        total_chunks = await asyncio.to_thread(self._indexer.index, event)
        logger.info(
            "Indexing complete courseId=%s total_chunks=%d",
            event.courseId,
            total_chunks,
        )