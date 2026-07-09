package com.cloudfuze.mft.common;

import org.springframework.http.HttpStatus;

/** A domain error carrying the HTTP status the API should return. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
