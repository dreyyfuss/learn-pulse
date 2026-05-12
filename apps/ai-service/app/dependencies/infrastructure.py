import httpx
from fastapi import Header, HTTPException, Request
from redis.asyncio import Redis

from app.rag.embedder import Embedder
from app.rag.pipeline import RagPipeline


def get_redis(request: Request) -> Redis:
    return request.app.state.redis


def get_http_client(request: Request) -> httpx.AsyncClient:
    return request.app.state.http_client


def get_embedder(request: Request) -> Embedder:
    return request.app.state.embedder


def get_rag_pipeline(request: Request) -> RagPipeline:
    return request.app.state.rag_pipeline


def get_user_id(x_user_id: str = Header(default=None, alias="X-User-Id")) -> str:
    if not x_user_id:
        raise HTTPException(status_code=401, detail="Missing X-User-Id header")
    return x_user_id
