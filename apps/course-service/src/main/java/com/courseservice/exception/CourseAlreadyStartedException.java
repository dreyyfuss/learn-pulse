package com.courseservice.exception;

// Phase 3 — thrown when an instructor attempts to edit a locked course.
public class CourseAlreadyStartedException extends RuntimeException {
    public CourseAlreadyStartedException(String message) {
        super(message);
    }
}
