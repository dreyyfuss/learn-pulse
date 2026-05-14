import logging

from app.config.vector_store import get_chroma_client, get_collection
from app.rag.chunker import TextChunker
from app.rag.embedder import Embedder
from app.rag.extractors import extract_text
from app.schemas.course import CoursePublishedEvent, LessonSummary

logger = logging.getLogger(__name__)

_CONTENT_TYPES = frozenset({"VIDEO", "ARTICLE", "DOCUMENT"})


def _lesson_text(lesson: LessonSummary) -> str:
    parts = [f"{lesson.moduleTitle}: {lesson.title}"]
    if lesson.moduleDescription:
        parts.append(lesson.moduleDescription)
    if lesson.description:
        parts.append(lesson.description)
    return "\n".join(parts)


class CourseIndexer:
    def __init__(self, embedder: Embedder, chunker: TextChunker, fetcher=None, transcriber=None) -> None:
        self._embedder = embedder
        self._chunker = chunker
        self._fetcher = fetcher
        self._transcriber = transcriber
        client = get_chroma_client()
        self._collection = get_collection(client)

    def index(self, event: CoursePublishedEvent) -> int:
        """Chunk + embed all lessons and upsert into ChromaDB.

        Returns the total number of chunks stored.
        """
        logger.info(
            ">>> Indexing started courseId=%s title=%r lessons=%d",
            event.courseId,
            event.title,
            len(event.lessons),
        )
        total = 0
        for lesson in event.lessons:
            total += self._index_summary(event.courseId, lesson)
            total += self._index_content(event.courseId, lesson)

        logger.info(
            ">>> Indexing complete courseId=%s total_chunks=%d",
            event.courseId,
            total,
        )
        return total

    def _index_summary(self, course_id: str, lesson: LessonSummary) -> int:
        text = _lesson_text(lesson)
        chunks = self._chunker.chunk(text)
        if not chunks:
            return 0

        texts = [c.text for c in chunks]
        embeddings = self._embedder.embed(texts)

        ids = [
            f"course-{course_id}-lesson-{lesson.lessonId}-chunk-{c.index}"
            for c in chunks
        ]
        metadatas = [
            {
                "courseId": course_id,
                "lessonId": lesson.lessonId,
                "lessonTitle": lesson.title,
                "moduleId": lesson.moduleId,
                "moduleTitle": lesson.moduleTitle,
                "chunkIndex": c.index,
                "chunkType": "summary",
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
            "Indexed summary lessonId=%s title=%r chunks=%d",
            lesson.lessonId,
            lesson.title,
            len(chunks),
        )
        return len(chunks)

    def _index_content(self, course_id: str, lesson: LessonSummary) -> int:
        if not lesson.contentKey:
            logger.info(
                "  [%s] no contentKey — skipping content indexing", lesson.title
            )
            return 0
        if lesson.contentType not in _CONTENT_TYPES:
            logger.info(
                "  [%s] contentType=%s not indexable — skipping",
                lesson.title,
                lesson.contentType,
            )
            return 0
        if self._fetcher is None:
            logger.warning("  [%s] ContentFetcher not configured", lesson.title)
            return 0

        logger.info(
            "  [%s] fetching content key=%s type=%s",
            lesson.title,
            lesson.contentKey,
            lesson.contentType,
        )
        try:
            raw = self._fetcher.fetch(lesson.contentKey)
            logger.info(
                "  [%s] fetched %d bytes from MinIO", lesson.title, len(raw)
            )

            if lesson.contentType == "VIDEO":
                if self._transcriber is None:
                    logger.warning("  [%s] VideoTranscriber not configured", lesson.title)
                    return 0
                logger.info("  [%s] starting transcription via Groq Whisper", lesson.title)
                text = self._transcriber.transcribe(raw, lesson.contentKey)
                logger.info(
                    "  [%s] transcription returned %d chars", lesson.title, len(text)
                )
            else:
                logger.info("  [%s] extracting text from %s", lesson.title, lesson.contentKey)
                text = extract_text(raw, lesson.contentKey)
                logger.info(
                    "  [%s] extracted %d chars", lesson.title, len(text)
                )

            if not text.strip():
                logger.warning(
                    "  [%s] empty text after extraction — nothing to index", lesson.title
                )
                return 0

            chunks = self._chunker.chunk(text)
            if not chunks:
                logger.warning("  [%s] chunker returned no chunks", lesson.title)
                return 0

            logger.info("  [%s] embedding %d chunks", lesson.title, len(chunks))
            texts = [c.text for c in chunks]
            embeddings = self._embedder.embed(texts)

            ids = [
                f"course-{course_id}-lesson-{lesson.lessonId}-content-chunk-{c.index}"
                for c in chunks
            ]
            metadatas = [
                {
                    "courseId": course_id,
                    "lessonId": lesson.lessonId,
                    "lessonTitle": lesson.title,
                    "moduleId": lesson.moduleId,
                    "moduleTitle": lesson.moduleTitle,
                    "chunkIndex": c.index,
                    "chunkType": "content",
                    "contentType": lesson.contentType,
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
                "  [%s] stored %d content chunks in ChromaDB",
                lesson.title,
                len(chunks),
            )
            return len(chunks)

        except Exception:
            logger.exception(
                "  [%s] content indexing failed key=%s",
                lesson.title,
                lesson.contentKey,
            )
            return 0
