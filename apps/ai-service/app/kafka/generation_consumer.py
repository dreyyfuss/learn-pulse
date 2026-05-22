from __future__ import annotations

import asyncio
import json
import logging
import uuid

from aiokafka import AIOKafkaConsumer
from pydantic import ValidationError

from app.config.settings import settings
from app.generation.pipeline import CourseGenerationPipeline
from app.kafka.producer import GenerationEventProducer
from app.middleware.trace import trace_id_var
from app.schemas.generation import CourseGenerationRequestedEvent

logger = logging.getLogger(__name__)


class CourseGenerationConsumer:
    def __init__(
        self,
        pipeline: CourseGenerationPipeline,
        producer: GenerationEventProducer,
    ) -> None:
        self._pipeline = pipeline
        self._producer = producer
        self._consumer: AIOKafkaConsumer | None = None
        self._task: asyncio.Task | None = None

    async def start(self) -> None:
        self._consumer = AIOKafkaConsumer(
            settings.kafka_topic_course_generation_requested,
            bootstrap_servers=settings.kafka_bootstrap_servers,
            group_id=settings.kafka_group_id_generation,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
        )
        await self._consumer.start()
        self._task = asyncio.create_task(self._consume())
        logger.info(
            "CourseGenerationConsumer started topic=%s group=%s",
            settings.kafka_topic_course_generation_requested,
            settings.kafka_group_id_generation,
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
        logger.info("CourseGenerationConsumer stopped")

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
        raw_trace = next(
            (v for k, v in (msg.headers or []) if k == "trace-id"),
            None,
        )
        trace_id = (
            raw_trace.decode() if isinstance(raw_trace, bytes) else raw_trace
        ) or str(uuid.uuid4())
        token = trace_id_var.set(trace_id)

        try:
            raw = msg.value.decode("utf-8") if isinstance(msg.value, bytes) else msg.value
            try:
                event = CourseGenerationRequestedEvent.model_validate(json.loads(raw))
            except (json.JSONDecodeError, ValidationError):
                logger.exception(
                    "Invalid course.generation.requested payload offset=%s", msg.offset
                )
                return

            logger.info(
                "course.generation.requested received jobId=%s instructorId=%s",
                event.jobId,
                event.instructorId,
            )

            try:
                payload = await self._pipeline.generate(event)
                await self._producer.publish_completed(payload)
            except Exception as exc:
                logger.exception("Generation pipeline failed jobId=%s", event.jobId)
                await self._producer.publish_failed(
                    event.jobId, event.instructorId, str(exc)
                )
        finally:
            trace_id_var.reset(token)
