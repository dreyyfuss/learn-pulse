from pydantic import BaseModel


class Instructor(BaseModel):
    id: str
    fullName: str


class LessonSummary(BaseModel):
    lessonId: str
    title: str
    description: str | None = None
    contentType: str | None = None
    moduleId: str
    moduleTitle: str
    moduleDescription: str | None = None


class CoursePublishedEvent(BaseModel):
    eventId: str
    eventType: str
    version: int
    occurredAt: str
    courseId: str
    title: str
    instructor: Instructor
    lessons: list[LessonSummary]