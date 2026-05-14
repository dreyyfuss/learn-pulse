import asyncio
import logging
from collections.abc import AsyncIterator

from langchain_cerebras import ChatCerebras
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.config.settings import settings
from app.config.vector_store import get_chroma_client, get_collection
from app.rag.embedder import Embedder

logger = logging.getLogger(__name__)

from langchain_cerebras import ChatCerebras
from langchain.schema import HumanMessage, SystemMessage

class RagPipeline:
    def __init__(self, embedder: Embedder) -> None:
        self._embedder = embedder
        self._llm: ChatCerebras | None = None  # lazy — needs a valid API key
        client = get_chroma_client()
        self._collection = get_collection(client)

    def _llm_instance(self) -> ChatCerebras:
        if self._llm is None:
            if not settings.cerebras_api_key:
                raise RuntimeError("CEREBRAS_API_KEY is not set")
            self._llm = ChatCerebras(
                api_key=settings.cerebras_api_key,
                model=settings.cerebras_model,
            )
        return self._llm

    def _retrieve(self, query: str, course_id: str) -> list[str]:
        embedding = self._embedder.embed([query])[0]
        results = self._collection.query(
            query_embeddings=[embedding],
            n_results=settings.rag_top_k,
            where={"courseId": course_id},
        )
        docs: list[str] = results.get("documents", [[]])[0]
        logger.debug("Retrieved %d chunks courseId=%s", len(docs), course_id)
        return docs

    async def stream(
        self,
        query: str,
        course_id: str,
        course_title: str,
        history: list[dict],
    ) -> AsyncIterator[str]:
        chunks = await asyncio.to_thread(self._retrieve, query, course_id)
        context = "\n\n".join(chunks) if chunks else "No relevant content found."

        system_content = (
            f'You are a study assistant for the course "{course_title}".\n'
            "Answer questions using ONLY the course content provided below.\n"
            "If the question cannot be answered from the course content, "
            "politely say so and suggest the learner review the relevant lessons.\n\n"
            f"Course content:\n{context}"
        )

        messages: list = [SystemMessage(content=system_content)]
        for msg in history:
            role = msg.get("role")
            content = msg.get("content", "")
            if role == "user":
                messages.append(HumanMessage(content=content))
            elif role == "assistant":
                messages.append(AIMessage(content=content))
        messages.append(HumanMessage(content=query))

        async for chunk in self._llm_instance().astream(messages):
            if chunk.content:
                yield chunk.content
