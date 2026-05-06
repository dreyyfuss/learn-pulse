from contextlib import asynccontextmanager

from fastapi import FastAPI


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Phase 4: start aiokafka consumer for course.published here
    yield
    # Phase 4: graceful consumer shutdown here


app = FastAPI(title="LearnPulse AI Service", version="0.1.0", lifespan=lifespan)


@app.get("/healthz")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=9000, reload=True)
