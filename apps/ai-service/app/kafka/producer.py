from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone

from aiokafka import AIOKafkaProducer

from app.config.settings import settings

logger = logging.getLogger(__name__)


class GenerationEventProducer:
    def __init__(self) -> None:
        self._producer: AIOKafkaProducer | None = None

    async def start(self) -> None:
        self._producer = AIOKafkaProducer(
            bootstrap_servers=settings.kafka_bootstrap_servers,
        )
        await self._producer.start()
        logger.info("GenerationEventProducer started")

    async def stop(self) -> None:
        if self._producer:
            await self._producer.stop()
        logger.info("GenerationEventProducer stopped")

    async def publish_completed(self, payload: dict) -> None:
        topic = settings.kafka_topic_course_generation_completed
        await self._send(topic, payload)
        logger.info(
            "Published course.generation.completed jobId=%s", payload.get("jobId")
        )

    async def publish_failed(self, job_id: str, instructor_id: str, reason: str) -> None:
        topic = settings.kafka_topic_course_generation_failed
        payload = {
            "eventId": str(uuid.uuid4()),
            "eventType": "course.generation.failed",
            "version": 1,
            "occurredAt": datetime.now(timezone.utc).isoformat(),
            "jobId": job_id,
            "instructorId": instructor_id,
            "reason": reason,
        }
        await self._send(topic, payload)
        logger.info("Published course.generation.failed jobId=%s", job_id)

    async def _send(self, topic: str, payload: dict) -> None:
        assert self._producer is not None, "Producer not started"
        value = json.dumps(payload).encode("utf-8")
        await self._producer.send_and_wait(topic, value=value)
