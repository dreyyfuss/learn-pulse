package com.courseservice.exception;

public class CourseNotPublishableException extends RuntimeException {
    public CourseNotPublishableException(String message) {
        super(message);
    }
}
