package com.chevereto.java.web;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 把站内相对路径拼成「当前请求 + 上下文路径」的绝对地址。
 *
 * <p>用在给用户复制的链接上（尤其是删图链接）：相对路径 {@code /image/x/delete/y}
 * 复制出去没有域名，粘到别处就失效了，不如直接给完整 URL。
 */
public final class WebUrls {

    private WebUrls() {
    }

    public static String absolute(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return pathOrUrl;
        }
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        String p = pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl;
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().path(p).toUriString();
        } catch (IllegalStateException e) {
            // 不在 HTTP 请求线程里（比如定时任务），退回相对路径
            return p;
        }
    }
}
