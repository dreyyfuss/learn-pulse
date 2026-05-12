from typing import Annotated

import httpx
from fastapi import Depends
from redis.asyncio import Redis

from app.dependencies.infrastructure import get_http_client, get_rag_pipeline, get_redis
from app.rag.pipeline import RagPipeline
from app.services.chat_service import ChatService


def get_chat_service(
    redis: Annotated[Redis, Depends(get_redis)],
    http: Annotated[httpx.AsyncClient, Depends(get_http_client)],
    pipeline: Annotated[RagPipeline, Depends(get_rag_pipeline)],
) -> ChatService:
    return ChatService(redis=redis, pipeline=pipeline, http=http)
