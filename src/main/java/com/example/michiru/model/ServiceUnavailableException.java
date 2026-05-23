package com.example.michiru.model;

// Runtime error for Groq/network/config failures; httpStatus is response code or -1.

public class ServiceUnavailableException extends RuntimeException {

    private final int httpStatus;

    public ServiceUnavailableException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
