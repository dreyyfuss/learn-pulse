from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI
from redis.asyncio import Redis

from app.config.settings import get_settings
from app.generation.pipeline import CourseGenerationPipeline
from app.kafka.consumer import CoursePublishedConsumer
from app.kafka.generation_consumer import CourseGenerationConsumer
from app.kafka.producer import GenerationEventProducer
from app.rag.chunker import TextChunker
from app.rag.content_fetcher import ContentFetcher
from app.rag.embedder import Embedder
from app.rag.indexer import CourseIndexer
from app.rag.pipeline import RagPipeline
from app.rag.transcriber import VideoTranscriber


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()

    embedder = Embedder()
    embedder.load()

    chunker = TextChunker(chunk_size=settings.chunk_size, overlap=settings.chunk_overlap)
    fetcher = ContentFetcher()
    transcriber = VideoTranscriber()
    indexer = CourseIndexer(embedder=embedder, chunker=chunker, fetcher=fetcher, transcriber=transcriber)
    consumer = CoursePublishedConsumer(indexer=indexer)
    await consumer.start()

    gen_pipeline = CourseGenerationPipeline()
    gen_producer = GenerationEventProducer()
    await gen_producer.start()
    gen_consumer = CourseGenerationConsumer(pipeline=gen_pipeline, producer=gen_producer)
    await gen_consumer.start()

    app.state.embedder = embedder
    app.state.redis = Redis.from_url(settings.redis_url, decode_responses=True)
    app.state.http_client = httpx.AsyncClient()
    app.state.rag_pipeline = RagPipeline(embedder=embedder)
    app.state.consumer = consumer
    app.state.gen_consumer = gen_consumer
    app.state.gen_producer = gen_producer

    yield

    await consumer.stop()
    await gen_consumer.stop()
    await gen_producer.stop()
    await app.state.redis.aclose()
    await app.state.http_client.aclose()
