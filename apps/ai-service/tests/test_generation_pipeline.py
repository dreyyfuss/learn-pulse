import json
import pytest
from datetime import datetime
from unittest.mock import AsyncMock, MagicMock, patch

from app.generation.pipeline import CourseGenerationPipeline
from app.schemas.generation import (
    CourseGenerationRequestedEvent,
    CourseOutline,
    GeneratedQuiz,
    LessonOutline,
    ModuleOutline,
)


# ── Shared test data ──────────────────────────────────────────────────────────

VALID_EVENT_DICT = {
    "eventId": "evt-001",
    "eventType": "course.generation.requested",
    "version": 1,
    "occurredAt": "2024-01-15T10:00:00Z",
    "jobId": "job-123",
    "instructorId": "inst-42",
    "prompt": "Create a Python course for beginners",
}

VALID_QUIZ_JSON = json.dumps({
    "title": "Variables Quiz",
    "passingScore": 70,
    "questions": [
        {
            "questionText": "What is a variable?",
            "questionType": "MCQ",
            "orderIndex": 1,
            "options": [
                {"optionText": "A storage location", "isCorrect": True, "orderIndex": 1},
                {"optionText": "A function", "isCorrect": False, "orderIndex": 2},
                {"optionText": "A loop", "isCorrect": False, "orderIndex": 3},
                {"optionText": "A module", "isCorrect": False, "orderIndex": 4},
            ],
        }
    ],
})


def make_event(**overrides) -> CourseGenerationRequestedEvent:
    return CourseGenerationRequestedEvent(**(VALID_EVENT_DICT | overrides))


def make_outline(n_modules: int = 1, n_lessons: int = 2) -> CourseOutline:
    modules = [
        ModuleOutline(
            title=f"Module {m}",
            description=f"Module {m} description",
            orderIndex=m,
            lessons=[
                LessonOutline(
                    title=f"Lesson {m}.{l}",
                    description=f"Lesson {m}.{l} description",
                    orderIndex=l,
                )
                for l in range(1, n_lessons + 1)
            ],
        )
        for m in range(1, n_modules + 1)
    ]
    return CourseOutline(
        title="Python Basics",
        description="Learn Python from scratch.",
        category="Programming",
        modules=modules,
    )


def make_lesson_results(outline: CourseOutline) -> list[dict]:
    """Build the lesson_results list that _build_completed_payload expects."""
    results = []
    for mod in outline.modules:
        for les in mod.lessons:
            quiz = GeneratedQuiz.model_validate(json.loads(VALID_QUIZ_JSON))
            results.append({
                "moduleOrderIndex": mod.orderIndex,
                "lessonOrderIndex": les.orderIndex,
                "content": f"## {les.title}\n\nLesson content here.",
                "quiz": quiz,
            })
    return results


def make_llm_mock(quiz_json: str = VALID_QUIZ_JSON) -> MagicMock:
    """LLM whose abatch() returns mocked responses; content = quiz_json for all."""
    async def fake_abatch(messages):
        responses = []
        for _ in messages:
            msg = MagicMock()
            msg.content = quiz_json
            responses.append(msg)
        return responses

    mock_llm = MagicMock()
    mock_llm.abatch = fake_abatch
    return mock_llm


@pytest.fixture
def pipeline() -> CourseGenerationPipeline:
    return CourseGenerationPipeline()


# ── _parse_json ───────────────────────────────────────────────────────────────

class TestParseJson:
    def test_parses_clean_json(self, pipeline):
        assert pipeline._parse_json('{"key": "value"}') == {"key": "value"}

    def test_strips_json_markdown_fence(self, pipeline):
        text = '```json\n{"key": "value"}\n```'
        assert pipeline._parse_json(text) == {"key": "value"}

    def test_strips_plain_markdown_fence(self, pipeline):
        text = '```\n{"key": "value"}\n```'
        assert pipeline._parse_json(text) == {"key": "value"}

    def test_handles_surrounding_whitespace(self, pipeline):
        assert pipeline._parse_json('  {"key": "value"}  ') == {"key": "value"}

    def test_raises_on_invalid_json(self, pipeline):
        with pytest.raises(json.JSONDecodeError):
            pipeline._parse_json("not valid json at all")

    def test_parses_nested_json(self, pipeline):
        data = {"modules": [{"title": "Intro", "lessons": []}]}
        assert pipeline._parse_json(json.dumps(data)) == data


# ── _llm_instance ─────────────────────────────────────────────────────────────

class TestLlmInstance:
    def test_raises_without_groq_api_key(self, pipeline):
        pipeline._llm = None
        with patch("app.generation.pipeline.settings") as mock_settings:
            mock_settings.groq_api_key = ""
            with pytest.raises(RuntimeError, match="GROQ_API_KEY"):
                pipeline._llm_instance()

    def test_returns_cached_instance_on_second_call(self, pipeline):
        sentinel = MagicMock()
        pipeline._llm = sentinel
        assert pipeline._llm_instance() is sentinel

    def test_does_not_recreate_if_already_set(self, pipeline):
        sentinel = MagicMock()
        pipeline._llm = sentinel
        pipeline._llm_instance()
        pipeline._llm_instance()
        assert pipeline._llm is sentinel  # still the same object


# ── _content_user_msg ─────────────────────────────────────────────────────────

class TestContentUserMsg:
    def _get_parts(self):
        outline = make_outline()
        mod = outline.modules[0]
        les = mod.lessons[0]
        return mod, les

    def test_includes_course_prompt(self, pipeline):
        mod, les = self._get_parts()
        msg = pipeline._content_user_msg("Build a FastAPI course", mod, les)
        assert "Build a FastAPI course" in msg

    def test_includes_module_title(self, pipeline):
        mod, les = self._get_parts()
        msg = pipeline._content_user_msg("prompt", mod, les)
        assert mod.title in msg

    def test_includes_lesson_title(self, pipeline):
        mod, les = self._get_parts()
        msg = pipeline._content_user_msg("prompt", mod, les)
        assert les.title in msg

    def test_includes_lesson_description(self, pipeline):
        mod, les = self._get_parts()
        msg = pipeline._content_user_msg("prompt", mod, les)
        assert les.description in msg


# ── _quiz_user_msg ────────────────────────────────────────────────────────────

class TestQuizUserMsg:
    def test_includes_lesson_title(self, pipeline):
        les = LessonOutline(title="Intro to Variables", description="Variables basics", orderIndex=1)
        msg = pipeline._quiz_user_msg(les)
        assert "Intro to Variables" in msg

    def test_includes_lesson_description(self, pipeline):
        les = LessonOutline(title="Loops", description="For and while loops in Python", orderIndex=2)
        msg = pipeline._quiz_user_msg(les)
        assert "For and while loops in Python" in msg


# ── _build_completed_payload ──────────────────────────────────────────────────

class TestBuildCompletedPayload:
    def test_event_type_is_course_generation_completed(self, pipeline):
        outline = make_outline()
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        assert payload["eventType"] == "course.generation.completed"

    def test_version_is_one(self, pipeline):
        outline = make_outline()
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        assert payload["version"] == 1

    def test_job_id_matches_event(self, pipeline):
        outline = make_outline()
        payload = pipeline._build_completed_payload(
            make_event(jobId="job-xyz"), outline, make_lesson_results(outline)
        )
        assert payload["jobId"] == "job-xyz"

    def test_instructor_id_matches_event(self, pipeline):
        outline = make_outline()
        payload = pipeline._build_completed_payload(
            make_event(instructorId="inst-99"), outline, make_lesson_results(outline)
        )
        assert payload["instructorId"] == "inst-99"

    def test_event_id_is_a_uuid(self, pipeline):
        import uuid
        outline = make_outline()
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        uuid.UUID(payload["eventId"])  # raises if not valid UUID

    def test_occurred_at_is_iso_format(self, pipeline):
        outline = make_outline()
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        datetime.fromisoformat(payload["occurredAt"])  # raises if not valid ISO

    def test_course_title_comes_from_outline(self, pipeline):
        outline = make_outline()
        outline.title = "Advanced Rust"
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        assert payload["course"]["title"] == "Advanced Rust"

    def test_course_category_comes_from_outline(self, pipeline):
        outline = make_outline()
        outline.category = "Systems Programming"
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        assert payload["course"]["category"] == "Systems Programming"

    def test_module_count_matches_outline(self, pipeline):
        outline = make_outline(n_modules=3)
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        assert len(payload["course"]["modules"]) == 3

    def test_lesson_count_per_module_matches_outline(self, pipeline):
        outline = make_outline(n_modules=2, n_lessons=3)
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        for mod in payload["course"]["modules"]:
            assert len(mod["lessons"]) == 3

    def test_lesson_content_is_included(self, pipeline):
        outline = make_outline()
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        lesson = payload["course"]["modules"][0]["lessons"][0]
        assert lesson["content"] != ""

    def test_quiz_fields_present(self, pipeline):
        outline = make_outline()
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        quiz = payload["course"]["modules"][0]["lessons"][0]["quiz"]
        assert "title" in quiz
        assert "passingScore" in quiz
        assert "questions" in quiz

    def test_quiz_options_serialised_as_dicts(self, pipeline):
        outline = make_outline()
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        option = payload["course"]["modules"][0]["lessons"][0]["quiz"]["questions"][0]["options"][0]
        assert isinstance(option, dict)
        assert "optionText" in option
        assert "isCorrect" in option
        assert "orderIndex" in option

    def test_module_order_index_preserved(self, pipeline):
        outline = make_outline(n_modules=2)
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        assert payload["course"]["modules"][0]["orderIndex"] == 1
        assert payload["course"]["modules"][1]["orderIndex"] == 2

    def test_lesson_order_index_preserved(self, pipeline):
        outline = make_outline(n_lessons=2)
        payload = pipeline._build_completed_payload(make_event(), outline, make_lesson_results(outline))
        lessons = payload["course"]["modules"][0]["lessons"]
        assert lessons[0]["orderIndex"] == 1
        assert lessons[1]["orderIndex"] == 2


# ── generate (async) ──────────────────────────────────────────────────────────

class TestGenerate:
    async def test_calls_generate_outline_with_prompt(self, pipeline):
        outline = make_outline()
        pipeline._generate_outline = MagicMock(return_value=outline)
        pipeline._llm = make_llm_mock()

        await pipeline.generate(make_event(prompt="Build a React course"))

        pipeline._generate_outline.assert_called_once_with("Build a React course")

    async def test_payload_job_id_matches_event(self, pipeline):
        outline = make_outline()
        pipeline._generate_outline = MagicMock(return_value=outline)
        pipeline._llm = make_llm_mock()

        payload = await pipeline.generate(make_event(jobId="job-abc"))
        assert payload["jobId"] == "job-abc"

    async def test_payload_instructor_id_matches_event(self, pipeline):
        outline = make_outline()
        pipeline._generate_outline = MagicMock(return_value=outline)
        pipeline._llm = make_llm_mock()

        payload = await pipeline.generate(make_event(instructorId="inst-77"))
        assert payload["instructorId"] == "inst-77"

    async def test_payload_event_type_is_completed(self, pipeline):
        outline = make_outline()
        pipeline._generate_outline = MagicMock(return_value=outline)
        pipeline._llm = make_llm_mock()

        payload = await pipeline.generate(make_event())
        assert payload["eventType"] == "course.generation.completed"

    async def test_payload_contains_correct_module_count(self, pipeline):
        outline = make_outline(n_modules=2, n_lessons=2)
        pipeline._generate_outline = MagicMock(return_value=outline)
        pipeline._llm = make_llm_mock()

        payload = await pipeline.generate(make_event())
        assert len(payload["course"]["modules"]) == 2

    async def test_payload_contains_correct_lesson_count(self, pipeline):
        outline = make_outline(n_modules=1, n_lessons=3)
        pipeline._generate_outline = MagicMock(return_value=outline)
        pipeline._llm = make_llm_mock()

        payload = await pipeline.generate(make_event())
        assert len(payload["course"]["modules"][0]["lessons"]) == 3

    async def test_raises_on_malformed_quiz_json(self, pipeline):
        outline = make_outline()
        pipeline._generate_outline = MagicMock(return_value=outline)
        pipeline._llm = make_llm_mock(quiz_json="not valid json")

        with pytest.raises(Exception):
            await pipeline.generate(make_event())

    async def test_raises_on_quiz_with_wrong_schema(self, pipeline):
        outline = make_outline()
        pipeline._generate_outline = MagicMock(return_value=outline)
        pipeline._llm = make_llm_mock(quiz_json='{"unexpected": "structure"}')

        with pytest.raises(Exception):
            await pipeline.generate(make_event())

    async def test_content_is_included_in_each_lesson(self, pipeline):
        outline = make_outline(n_modules=1, n_lessons=2)
        pipeline._generate_outline = MagicMock(return_value=outline)
        pipeline._llm = make_llm_mock()

        payload = await pipeline.generate(make_event())
        for lesson in payload["course"]["modules"][0]["lessons"]:
            assert lesson["content"] != ""
