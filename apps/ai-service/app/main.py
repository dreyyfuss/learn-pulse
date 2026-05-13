import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

# Make all app.* loggers visible at INFO level in docker logs
logging.getLogger("app").setLevel(logging.INFO)

from app.api.chat import router as chat_router
from app.exceptions import EnrolmentError
from app.lifespan import lifespan
from app.middleware.logging import RequestIDMiddleware

app = FastAPI(title="LearnPulse AI Service", version="0.1.0", lifespan=lifespan)

app.add_middleware(RequestIDMiddleware)
app.include_router(chat_router)


@app.exception_handler(EnrolmentError)
async def enrolment_error_handler(request: Request, exc: EnrolmentError) -> JSONResponse:
    return JSONResponse(status_code=403, content={"detail": str(exc)})


@app.get("/healthz")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=9000, reload=True)
