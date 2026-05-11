from pydantic import BaseModel


class Instructor(BaseModel):
    id: int
    fullName: str


class LessonSummary(BaseModel):
    lessonId: int
    title: str
    description: str | None = None
    contentType: str | None = None
    moduleId: int
    moduleTitle: str
    moduleDescription: str | None = None


class CoursePublishedEvent(BaseModel):
    eventId: str
    eventType: str
    version: int
    occurredAt: str
    courseId: int
    title: str
    instructor: Instructor
    lessons: list[LessonSummary]