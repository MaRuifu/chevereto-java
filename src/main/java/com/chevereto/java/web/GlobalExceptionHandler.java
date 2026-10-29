package com.chevereto.java.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** 全局异常处理：API 请求返回 JSON，页面请求渲染错误页 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public Object handleApiException(ApiException e, HttpServletRequest request, Model model) {
        if (isApiPath(request)) {
            return jsonError(e.getStatus(), e.getMessage());
        }
        return errorPage(model, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request, Model model) {
        if (isApiPath(request)) {
            return jsonError(HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大，超出服务器限制");
        }
        return errorPage(model, "上传文件过大，超出服务器限制");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(NoResourceFoundException e, HttpServletRequest request, Model model) {
        if (isApiPath(request)) {
            return jsonError(HttpStatus.NOT_FOUND, "资源不存在");
        }
        return errorPage(model, "页面不存在");
    }

    /**
     * 保留显式声明的状态码。没有这一段的话，下面的 {@code @ExceptionHandler(Exception.class)}
     * 会把 {@code ResponseStatusException(404)} 也吞掉，渲染成 HTTP 200 的错误页
     * （删图口令错误本该是 404，与原版 issueError(404) 一致）。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Object handleStatusException(ResponseStatusException e, HttpServletRequest request,
                                        Model model, HttpServletResponse response) {
        int code = e.getStatusCode().value();
        String reason = e.getReason() != null ? e.getReason()
                : (code == 404 ? "页面不存在" : "请求失败");
        if (isApiPath(request)) {
            return jsonError(HttpStatus.valueOf(code), reason);
        }
        response.setStatus(code);
        return errorPage(model, reason);
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception e, HttpServletRequest request, Model model) {
        log.error("请求 {} 处理异常", request.getRequestURI(), e);
        if (isApiPath(request)) {
            return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + e.getMessage());
        }
        return errorPage(model, "服务器内部错误：" + e.getMessage());
    }

    private Object jsonError(HttpStatus status, String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status_code", status.value());
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("message", message);
        err.put("code", status.value());
        resp.put("error", err);
        return ResponseEntity.status(status).body(resp);
    }

    private String errorPage(Model model, String message) {
        model.addAttribute("siteName", "Java 图床");
        model.addAttribute("errorMsg", message);
        return "error/generic";
    }

    private boolean isApiPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/") || uri.startsWith("/upload");
    }
}
