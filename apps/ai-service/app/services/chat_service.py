import hashlib
import logging
import re

from app.config.redis_client import get_redis
from app.config.settings import settings
from app.rag.pipeline import RagPipeline

logger = logging.getLogger(__name__)

_CACHE_PREFIX = "cache:ai:reply:"


def _normalise(message: str) -> str:
    return re.sub(r"\s+", " ", message.strip().lower())


def _cache_key(course_id: str, message: str) -> str:
    raw = course_id + _normalise(message)
    return _CACHE_PREFIX + hashlib.sha256(raw.encode()).hexdigest()


async def chat(pipeline: RagPipeline, course_id: str, message: str) -> tuple[str, bool]:
    """Returns (reply, cache_hit)."""
    key = _cache_key(course_id, message)
    redis = await get_redis()

    cached = await redis.get(key)
    if cached:
        logger.info("cache hit key=%s", key)
        return cached, True

    reply = await pipeline.query(question=message, course_id=course_id)
    await redis.set(key, reply, ex=settings.redis_ttl_seconds)
    logger.info("cache set key=%s ttl=%ds", key, settings.redis_ttl_seconds)
    return reply, False
