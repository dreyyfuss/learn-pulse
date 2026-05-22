from __future__ import annotations

import asyncio
import json
import logging
import re
import time
import uuid
from datetime import datetime, timezone

from langchain_groq import ChatGroq
from langchain_core.messages import HumanMessage, SystemMessage

from app.config.settings import settings
from app.schemas.generation import (
    CourseGenerationRequestedEvent,
    CourseOutline,
    GeneratedQuiz,
    LessonOutline,
    ModuleOutline,
)

logger = logging.getLogger(__name__)

_OUTLINE_SYSTEM = (
    "You are a curriculum design expert. Given an instructor's course description, "
    "generate a complete structured course outline with 3–5 modules, each containing "
    "3–5 lessons. Return ONLY a valid JSON object — no markdown fences, no prose, "
    "no explanation. The JSON must match this schema exactly:\n"
    '{"title": str, "description": str, "category": str, "modules": ['
    '{"title": str, "description": str, "orderIndex": int, "lessons": ['
    '{"title": str, "description": str, "orderIndex": int}]}]}'
)

_CONTENT_SYSTEM = (
    "You are an expert technical writer and educator. Write comprehensive, engaging "
    "lesson content in Markdown format. Include explanations, examples, and code "
    "snippets where appropriate. Aim for 400–800 words."
)

_QUIZ_SYSTEM = (
    "You are a quiz author. Given a lesson title and description, generate a short "
    "quiz with 3–5 questions mixing MCQ (4 options, one correct) and TRUE_FALSE "
    "(2 options: True/False). Return ONLY a valid JSON object — no markdown fences, "
    "no prose, no explanation. The JSON must match this schema exactly:\n"
    '{"title": str, "passingScore": int, "questions": ['
    '{"questionText": str, "questionType": "MCQ"|"TRUE_FALSE", "orderIndex": int, '
    '"options": [{"optionText": str, "isCorrect": bool, "orderIndex": int}]}]}'
)


class CourseGenerationPipeline:
    def __init__(self) -> None:
        self._llm: ChatGroq | None = None

    def _llm_instance(self) -> ChatGroq:
        if self._llm is None:
            if not settings.groq_api_key:
                raise RuntimeError("GROQ_API_KEY is not set")
            self._llm = ChatGroq(
                model=settings.groq_llm_model,
                api_key=settings.groq_api_key,
            )
            logger.info("LLM initialised model=%s", settings.groq_llm_model)
        return self._llm

    async def generate(self, event: CourseGenerationRequestedEvent) -> dict:
        t_start = time.monotonic()
        logger.info(
            "Generation started jobId=%s instructorId=%s prompt_len=%d",
            event.jobId, event.instructorId, len(event.prompt),
        )

        logger.info("Calling LLM for outline jobId=%s", event.jobId)
        t0 = time.monotonic()
        outline: CourseOutline = await asyncio.to_thread(
            self._generate_outline, event.prompt
        )
        total_lessons = sum(len(m.lessons) for m in outline.modules)
        logger.info(
            "Outline ready jobId=%s title=%r modules=%d lessons=%d elapsed=%.1fs",
            event.jobId, outline.title, len(outline.modules), total_lessons,
            time.monotonic() - t0,
        )
        for mod in outline.modules:
            logger.debug(
                "  module[%d] %r lessons=%d",
                mod.orderIndex, mod.title, len(mod.lessons),
            )

        all_lessons = [
            (mod, les)
            for mod in outline.modules
            for les in mod.lessons
        ]

        content_msgs = [
            [SystemMessage(content=_CONTENT_SYSTEM), HumanMessage(content=self._content_user_msg(event.prompt, mod, les))]
            for mod, les in all_lessons
        ]
        quiz_msgs = [
            [SystemMessage(content=_QUIZ_SYSTEM), HumanMessage(content=self._quiz_user_msg(les))]
            for mod, les in all_lessons
        ]

        logger.info(
            "Batching content + quizzes jobId=%s lessons=%d",
            event.jobId, len(all_lessons),
        )
        t1 = time.monotonic()
        llm = self._llm_instance()
        content_responses, quiz_responses = await asyncio.gather(
            llm.abatch(content_msgs),
            llm.abatch(quiz_msgs),
        )
        logger.info(
            "Batch complete jobId=%s lessons=%d elapsed=%.1fs",
            event.jobId, len(all_lessons), time.monotonic() - t1,
        )

        lesson_results = []
        for i, (mod, les) in enumerate(all_lessons):
            try:
                quiz = GeneratedQuiz.model_validate(self._parse_json(quiz_responses[i].content))
            except Exception:
                logger.exception(
                    "Quiz parse failed jobId=%s module=%r lesson=%r raw=%r",
                    event.jobId, mod.title, les.title, quiz_responses[i].content[:200],
                )
                raise
            logger.debug(
                "Lesson parsed jobId=%s module[%d] lesson[%d] %r content_len=%d questions=%d",
                event.jobId, mod.orderIndex, les.orderIndex, les.title,
                len(content_responses[i].content), len(quiz.questions),
            )
            lesson_results.append({
                "moduleOrderIndex": mod.orderIndex,
                "lessonOrderIndex": les.orderIndex,
                "content": content_responses[i].content,
                "quiz": quiz,
            })

        payload = self._build_completed_payload(event, outline, lesson_results)
        logger.info(
            "Generation complete jobId=%s title=%r modules=%d lessons=%d total_elapsed=%.1fs",
            event.jobId, outline.title, len(outline.modules), len(all_lessons),
            time.monotonic() - t_start,
        )
        return payload

    def _parse_json(self, text: str) -> dict:
        stripped = re.sub(r"^```(?:json)?\s*|\s*```$", "", text.strip(), flags=re.MULTILINE)
        return json.loads(stripped.strip())

    def _generate_outline(self, prompt: str) -> CourseOutline:
        resp = self._llm_instance().invoke(
            [SystemMessage(content=_OUTLINE_SYSTEM), HumanMessage(content=prompt)]
        )
        try:
            return CourseOutline.model_validate(self._parse_json(resp.content))
        except Exception:
            logger.exception("Outline parse failed raw=%r", resp.content[:200])
            raise

    def _content_user_msg(self, course_prompt: str, mod: ModuleOutline, les: LessonOutline) -> str:
        return (
            f"Course context: {course_prompt}\n\n"
            f"Module: {mod.title}\n"
            f"Lesson: {les.title}\n"
            f"Description: {les.description}\n\n"
            "Write comprehensive lesson content in Markdown."
        )

    def _quiz_user_msg(self, les: LessonOutline) -> str:
        return (
            f"Lesson title: {les.title}\n"
            f"Lesson description: {les.description}\n\n"
            "Generate a quiz for this lesson."
        )

    def _build_completed_payload(
        self,
        event: CourseGenerationRequestedEvent,
        outline: CourseOutline,
        lesson_results: list[dict],
    ) -> dict:
        result_map: dict[tuple[int, int], dict] = {
            (r["moduleOrderIndex"], r["lessonOrderIndex"]): r
            for r in lesson_results
        }

        modules_payload = []
        for mod in outline.modules:
            lessons_payload = []
            for les in mod.lessons:
                r = result_map[(mod.orderIndex, les.orderIndex)]
                quiz: GeneratedQuiz = r["quiz"]
                lessons_payload.append({
                    "title": les.title,
                    "description": les.description,
                    "orderIndex": les.orderIndex,
                    "content": r["content"],
                    "quiz": {
                        "title": quiz.title,
                        "passingScore": quiz.passingScore,
                        "questions": [
                            {
                                "questionText": q.questionText,
                                "questionType": q.questionType,
                                "orderIndex": q.orderIndex,
                                "options": [
                                    {
                                        "optionText": o.optionText,
                                        "isCorrect": o.isCorrect,
                                        "orderIndex": o.orderIndex,
                                    }
                                    for o in q.options
                                ],
                            }
                            for q in quiz.questions
                        ],
                    },
                })
            modules_payload.append({
                "title": mod.title,
                "description": mod.description,
                "orderIndex": mod.orderIndex,
                "lessons": lessons_payload,
            })

        return {
            "eventId": str(uuid.uuid4()),
            "eventType": "course.generation.completed",
            "version": 1,
            "occurredAt": datetime.now(timezone.utc).isoformat(),
            "jobId": event.jobId,
            "instructorId": event.instructorId,
            "course": {
                "title": outline.title,
                "description": outline.description,
                "category": outline.category,
                "modules": modules_payload,
            },
        }
