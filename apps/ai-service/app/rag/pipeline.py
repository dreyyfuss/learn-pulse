import logging

from langchain_cerebras import ChatCerebras
from langchain.schema import HumanMessage, SystemMessage

from app.config.settings import settings
from app.config.vector_store import get_chroma_client, get_collection

logger = logging.getLogger(__name__)


class RagPipeline:
    def __init__(self) -> None:
        self._ready = False
        self._llm: ChatCerebras | None = None
        self._collection = None

    async def setup(self) -> None:
        self._llm = ChatCerebras(
            model=settings.cerebras_model,
            api_key=settings.cerebras_api_key,
        )
        client = get_chroma_client()
        self._collection = get_collection(client)
        self._ready = True
        logger.info("RagPipeline ready model=%s", settings.cerebras_model)

    async def query(self, question: str, course_id: str | None = None) -> str:
        if not self._ready:
            raise RuntimeError("RagPipeline not initialised — call setup() first")

        where = {"courseId": course_id} if course_id else None
        results = self._collection.query(
            query_texts=[question],
            n_results=4,
            where=where,
        )
        chunks = results["documents"][0] if results["documents"] else []
        context = "\n\n".join(chunks) if chunks else "No course content available."

        messages = [
            SystemMessage(content=(
                "You are a study assistant for an online course. "
                "Answer ONLY from the course content provided below. "
                "If the answer is not in the content, say so.\n\n"
                f"Course content:\n{context}"
            )),
            HumanMessage(content=question),
        ]
        response = await self._llm.ainvoke(messages)
        return response.content
