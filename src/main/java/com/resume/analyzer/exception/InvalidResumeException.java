package com.resume.analyzer.exception;

/**
 * Custom exception thrown when an uploaded resume file is invalid, corrupt,
 * empty, or in an unsupported format.
 */
public class InvalidResumeException extends RuntimeException {
    public InvalidResumeException(String message) {
        super(message);
    }

    public InvalidResumeException(String message, Throwable cause) {
        super(message, cause);
    }
}
