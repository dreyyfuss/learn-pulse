from app.config.settings import settings


class RagPipeline:
    """Retrieval-Augmented Generation pipeline stub.

    Phase 2 will wire ChromaDB retrieval + Cerebras LLM here.
    """

    def __init__(self) -> None:
        self._ready = False

    async def setup(self) -> None:
        # Phase 2: initialise ChromaDB client and Cerebras LLM
        self._ready = True

    async def query(self, question: str, course_id: str | None = None) -> str:
        if not self._ready:
            raise RuntimeError("RagPipeline not initialised")
        # Phase 2: retrieve context, call LLM, return answer
        return "not implemented"