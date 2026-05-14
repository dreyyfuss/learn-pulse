package com.courseservice.exception;

public class ModuleLockedForUserException extends RuntimeException {
    public ModuleLockedForUserException(String message) {
        super(message);
    }
}
