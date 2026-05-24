from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


# ── Incoming Kafka event ──────────────────────────────────────────────────────

class CourseGenerationRequestedEvent(BaseModel):
    eventId: str
    eventType: str
    version: int
    occurredAt: str
    jobId: str
    instructorId: str
    prompt: str


# ── LLM structured output: course outline ────────────────────────────────────

class LessonOutline(BaseModel):
    title: str = Field(description="Concise lesson title")
    description: str = Field(description="One-sentence summary of what this lesson covers")
    orderIndex: int = Field(description="1-based position within the module")


class ModuleOutline(BaseModel):
    title: str = Field(description="Module title")
    description: str = Field(description="One-sentence summary of the module")
    orderIndex: int = Field(description="1-based position within the course")
    lessons: list[LessonOutline] = Field(
        description="Ordered list of lessons in this module (3–5 lessons)"
    )


class CourseOutline(BaseModel):
    title: str = Field(description="Course title")
    description: str = Field(description="2–3 sentence overview of the course")
    category: str = Field(description="Short category tag, e.g. 'Backend Development'")
    modules: list[ModuleOutline] = Field(
        description="Ordered list of course modules (3–5 modules)"
    )


# ── LLM structured output: quiz ───────────────────────────────────────────────

class QuizOption(BaseModel):
    optionText: str = Field(description="The option text")
    isCorrect: bool = Field(description="Whether this is the correct answer")
    orderIndex: int = Field(description="1-based option position")


class QuizQuestion(BaseModel):
    questionText: str = Field(description="The full question text")
    questionType: Literal["MCQ", "TRUE_FALSE"] = Field(
        description="MCQ for multiple choice, TRUE_FALSE for true/false"
    )
    orderIndex: int = Field(description="1-based question position")
    options: list[QuizOption] = Field(
        description="For MCQ: 4 options with exactly one correct. For TRUE_FALSE: 2 options (True/False)."
    )


class GeneratedQuiz(BaseModel):
    title: str = Field(description="Quiz title, e.g. 'Lesson Quiz: <lesson title>'")
    passingScore: int = Field(default=70, description="Passing score percentage (default 70)")
    questions: list[QuizQuestion] = Field(
        description="3–5 questions mixing MCQ and TRUE_FALSE"
    )
