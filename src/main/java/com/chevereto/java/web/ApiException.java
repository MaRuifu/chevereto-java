package com.chevereto.java.web;

import org.springframework.http.HttpStatus;

/** 业务异常（携带 HTTP 状态码与用户可读消息） */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
