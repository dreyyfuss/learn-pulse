import pytest
from unittest.mock import MagicMock, call, patch

from app.rag.chunker import Chunk
from app.rag.indexer import CourseIndexer, _lesson_text
from app.schemas.course import CoursePublishedEvent, Instructor, LessonSummary
from tests.conftest import VALID_COURSE_EVENT


def make_lesson(**overrides) -> LessonSummary:
    defaults = dict(
        lessonId="lesson-1",
        title="Intro",
        description="An intro lesson",
        moduleId="mod-1",
        moduleTitle="Module 1",
        moduleDescription="First module overview",
    )
    return LessonSummary(**(defaults | overrides))


def make_event(*lessons: LessonSummary) -> CoursePublishedEvent:
    return CoursePublishedEvent(
        eventId="evt-1",
        eventType="course.published",
        version=1,
        occurredAt="2024-01-01T00:00:00Z",
        courseId="course-abc",
        title="Test Course",
        instructor=Instructor(id="inst-1", fullName="Jane Doe"),
        lessons=list(lessons),
    )


@pytest.fixture
def mock_collection():
    return MagicMock()


@pytest.fixture
def mock_embedder():
    embedder = MagicMock()
    embedder.embed.return_value = [[0.1, 0.2, 0.3]]
    return embedder


@pytest.fixture
def mock_chunker():
    chunker = MagicMock()
    chunker.chunk.return_value = [Chunk(text="Module 1: Intro\nFirst module overview\nAn intro lesson", index=0)]
    return chunker


@pytest.fixture
def indexer(mock_embedder, mock_chunker, mock_collection):
    with patch("app.rag.indexer.get_chroma_client"), \
         patch("app.rag.indexer.get_collection", return_value=mock_collection):
        return CourseIndexer(embedder=mock_embedder, chunker=mock_chunker)


class TestLessonText:
    def test_all_fields_present(self):
        lesson = make_lesson(
            title="Variables",
            moduleTitle="Module 1",
            moduleDescription="Module desc",
            description="Lesson desc",
        )
        assert _lesson_text(lesson) == "Module 1: Variables\nModule desc\nLesson desc"

    def test_without_module_description(self):
        lesson = make_lesson(
            title="Variables",
            moduleTitle="Module 1",
            moduleDescription=None,
            description="Lesson desc",
        )
        assert _lesson_text(lesson) == "Module 1: Variables\nLesson desc"

    def test_without_lesson_description(self):
        lesson = make_lesson(
            title="Variables",
            moduleTitle="Module 1",
            moduleDescription="Module desc",
            description=None,
        )
        assert _lesson_text(lesson) == "Module 1: Variables\nModule desc"

    def test_without_any_description(self):
        lesson = make_lesson(
            title="Variables",
            moduleTitle="Module 1",
            moduleDescription=None,
            description=None,
        )
        assert _lesson_text(lesson) == "Module 1: Variables"

    def test_title_always_included(self):
        lesson = make_lesson(title="Deep Learning", moduleTitle="Advanced")
        text = _lesson_text(lesson)
        assert text.startswith("Advanced: Deep Learning")


class TestCourseIndexerInit:
    def test_creates_chroma_client_and_collection_on_init(self, mock_collection):
        with patch("app.rag.indexer.get_chroma_client") as mock_client_fn, \
             patch("app.rag.indexer.get_collection", return_value=mock_collection) as mock_coll_fn:
            mock_client = MagicMock()
            mock_client_fn.return_value = mock_client
            CourseIndexer(embedder=MagicMock(), chunker=MagicMock())
            mock_client_fn.assert_called_once()
            mock_coll_fn.assert_called_once_with(mock_client)


class TestCourseIndexerIndex:
    def test_calls_chunker_once_per_lesson(self, indexer, mock_chunker):
        lesson1, lesson2 = make_lesson(lessonId="l1"), make_lesson(lessonId="l2", title="L2")
        indexer.index(make_event(lesson1, lesson2))
        assert mock_chunker.chunk.call_count == 2

    def test_calls_embedder_with_chunk_texts(self, indexer, mock_embedder, mock_chunker):
        mock_chunker.chunk.return_value = [
            Chunk(text="chunk A", index=0),
            Chunk(text="chunk B", index=1),
        ]
        mock_embedder.embed.return_value = [[0.1], [0.2]]
        indexer.index(make_event(make_lesson()))
        mock_embedder.embed.assert_called_once_with(["chunk A", "chunk B"])

    def test_upserts_with_correct_ids(self, indexer, mock_collection, mock_chunker):
        mock_chunker.chunk.return_value = [Chunk(text="hello", index=0)]
        indexer.index(make_event(make_lesson(lessonId="lesson-x")))
        ids = mock_collection.upsert.call_args.kwargs["ids"]
        assert ids == ["course-course-abc-lesson-lesson-x-chunk-0"]

    def test_upserts_multiple_chunk_ids(self, indexer, mock_collection, mock_chunker, mock_embedder):
        mock_chunker.chunk.return_value = [Chunk(text="a", index=0), Chunk(text="b", index=1)]
        mock_embedder.embed.return_value = [[0.1], [0.2]]
        indexer.index(make_event(make_lesson(lessonId="l1")))
        ids = mock_collection.upsert.call_args.kwargs["ids"]
        assert ids == [
            "course-course-abc-lesson-l1-chunk-0",
            "course-course-abc-lesson-l1-chunk-1",
        ]

    def test_upserts_correct_metadata(self, indexer, mock_collection, mock_chunker):
        mock_chunker.chunk.return_value = [Chunk(text="hello", index=0)]
        lesson = make_lesson(lessonId="l1", title="Intro", moduleId="m1", moduleTitle="Mod 1")
        indexer.index(make_event(lesson))
        meta = mock_collection.upsert.call_args.kwargs["metadatas"][0]
        assert meta == {
            "courseId": "course-abc",
            "lessonId": "l1",
            "lessonTitle": "Intro",
            "moduleId": "m1",
            "moduleTitle": "Mod 1",
            "chunkIndex": 0,
        }

    def test_upserts_documents_match_chunk_texts(self, indexer, mock_collection, mock_chunker):
        mock_chunker.chunk.return_value = [Chunk(text="the content", index=0)]
        indexer.index(make_event(make_lesson()))
        docs = mock_collection.upsert.call_args.kwargs["documents"]
        assert docs == ["the content"]

    def test_returns_total_chunk_count(self, indexer, mock_chunker, mock_embedder):
        mock_chunker.chunk.return_value = [Chunk(text="a", index=0), Chunk(text="b", index=1)]
        mock_embedder.embed.return_value = [[0.1], [0.2]]
        lesson1, lesson2 = make_lesson(lessonId="l1"), make_lesson(lessonId="l2", title="L2")
        total = indexer.index(make_event(lesson1, lesson2))
        assert total == 4  # 2 lessons × 2 chunks each

    def test_skips_lessons_with_no_chunks(self, indexer, mock_collection, mock_chunker):
        mock_chunker.chunk.return_value = []
        result = indexer.index(make_event(make_lesson()))
        mock_collection.upsert.assert_not_called()
        assert result == 0

    def test_empty_lessons_list_returns_zero(self, indexer):
        event = make_event()  # no lessons
        assert indexer.index(event) == 0

    def test_upserts_once_per_lesson(self, indexer, mock_collection, mock_chunker):
        lesson1, lesson2 = make_lesson(lessonId="l1"), make_lesson(lessonId="l2", title="L2")
        indexer.index(make_event(lesson1, lesson2))
        assert mock_collection.upsert.call_count == 2
