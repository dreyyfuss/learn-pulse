import logging

from app.config.vector_store import get_chroma_client, get_collection
from app.rag.chunker import TextChunker
from app.rag.embedder import Embedder
from app.schemas.course import CoursePublishedEvent, LessonSummary

logger = logging.getLogger(__name__)


def _lesson_text(lesson: LessonSummary) -> str:
    parts = [f"{lesson.moduleTitle}: {lesson.title}"]
    if lesson.moduleDescription:
        parts.append(lesson.moduleDescription)
    if lesson.description:
        parts.append(lesson.description)
    return "\n".join(parts)


class CourseIndexer:
    def __init__(self, embedder: Embedder, chunker: TextChunker) -> None:
        self._embedder = embedder
        self._chunker = chunker
        client = get_chroma_client()
        self._collection = get_collection(client)

    def index(self, event: CoursePublishedEvent) -> int:
        """Chunk + embed all lessons and upsert into ChromaDB.

        Returns the total number of chunks stored.
        """
        total = 0

        for lesson in event.lessons:
            text = _lesson_text(lesson)
            chunks = self._chunker.chunk(text)
            if not chunks:
                continue

            texts = [c.text for c in chunks]
            embeddings = self._embedder.embed(texts)

            ids = [
                f"course-{event.courseId}-lesson-{lesson.lessonId}-chunk-{c.index}"
                for c in chunks
            ]
            metadatas = [
                {
                    "courseId": event.courseId,
                    "lessonId": lesson.lessonId,
                    "lessonTitle": lesson.title,
                    "moduleId": lesson.moduleId,
                    "moduleTitle": lesson.moduleTitle,
                    "chunkIndex": c.index,
                }
                for c in chunks
            ]

            self._collection.upsert(
                ids=ids,
                embeddings=embeddings,
                documents=texts,
                metadatas=metadatas,
            )

            logger.info(
                "Indexed lessonId=%s title=%r chunks=%d",
                lesson.lessonId,
                lesson.title,
                len(chunks),
            )
            total += len(chunks)

        logger.info(
            "Course indexed courseId=%s lessons=%d total_chunks=%d",
            event.courseId,
            len(event.lessons),
            total,
        )
        return total