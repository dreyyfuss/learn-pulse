from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.chat import router as chat_router
from app.config.redis_client import close_redis
from app.config.settings import settings
from app.kafka.consumer import CoursePublishedConsumer
from app.middleware.trace import TraceMiddleware
from app.rag.chunker import TextChunker
from app.rag.embedder import Embedder
from app.rag.indexer import CourseIndexer
from app.rag.pipeline import RagPipeline


@asynccontextmanager
async def lifespan(app: FastAPI):
    embedder = Embedder()
    embedder.load()  # downloads/loads model once at startup

    chunker = TextChunker(
        chunk_size=settings.chunk_size,
        chunk_overlap=settings.chunk_overlap,
    )
    indexer = CourseIndexer(embedder=embedder, chunker=chunker)

    consumer = CoursePublishedConsumer(indexer=indexer)
    await consumer.start()

    pipeline = RagPipeline()
    await pipeline.setup()
    app.state.pipeline = pipeline

    yield

    await consumer.stop()
    await close_redis()


app = FastAPI(title="LearnPulse AI Service", version="0.1.0", lifespan=lifespan)
app.add_middleware(TraceMiddleware)

app.include_router(chat_router)


@app.get("/healthz")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=9000, reload=True)