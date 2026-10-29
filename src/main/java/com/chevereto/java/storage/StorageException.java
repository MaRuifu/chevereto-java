package com.chevereto.java.storage;

import org.springframework.http.HttpStatus;

/** 存储相关异常 */
public class StorageException extends RuntimeException {

    private final HttpStatus status;

    public StorageException(String message) {
        this(message, HttpStatus.INTERNAL_SERVER_ERROR, null);
    }

    public StorageException(String message, Throwable cause) {
        this(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public StorageException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public StorageException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
