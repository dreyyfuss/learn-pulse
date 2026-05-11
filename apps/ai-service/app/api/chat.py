from fastapi import APIRouter

router = APIRouter(prefix="/api/ai", tags=["ai"])


@router.get("/healthz")
async def health():
    return {"status": "ok"}