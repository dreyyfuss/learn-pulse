package com.courseservice.exception;

public class LessonOutOfOrderException extends RuntimeException {
    public LessonOutOfOrderException(String message) {
        super(message);
    }
}
