package com.courseservice.exception;

public class ModuleLockedException extends RuntimeException {
    public ModuleLockedException(String message) {
        super(message);
    }
}
