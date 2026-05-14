"""Shared test fixtures and data."""

VALID_COURSE_EVENT = {
    "eventId": "evt-001",
    "eventType": "course.published",
    "version": 1,
    "occurredAt": "2024-01-15T10:00:00Z",
    "courseId": "course-abc",
    "title": "Python Fundamentals",
    "instructor": {"id": "inst-1", "fullName": "Jane Doe"},
    "lessons": [
        {
            "lessonId": "lesson-1",
            "title": "Introduction",
            "description": "An intro to Python",
            "contentType": "video",
            "moduleId": "mod-1",
            "moduleTitle": "Getting Started",
            "moduleDescription": "First module overview",
        },
        {
            "lessonId": "lesson-2",
            "title": "Variables",
            "description": "Learn about variables",
            "contentType": "video",
            "moduleId": "mod-1",
            "moduleTitle": "Getting Started",
            "moduleDescription": "First module overview",
        },
    ],
}
