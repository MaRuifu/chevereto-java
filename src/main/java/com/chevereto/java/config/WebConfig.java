package com.chevereto.java.config;

import com.chevereto.java.web.AuthSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：
 * <ol>
 *   <li>首次安装闸门：实例未配置数据库时，所有页面强制跳到 /setup</li>
 *   <li>/admin/** 需要管理员</li>
 * </ol>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthSupport auth;
    private final InstanceConfig instanceConfig;

    public WebConfig(AuthSupport auth, InstanceConfig instanceConfig) {
        this.auth = auth;
        this.instanceConfig = instanceConfig;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SetupGateInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/setup", "/css/**", "/js/**", "/assets/**", "/vendor/**",
                        "/images/**", "/error", "/error/**", "/favicon.ico")
                .order(0);

        registry.addInterceptor(new AdminInterceptor())
                .addPathPatterns("/admin/**")
                .order(1);
    }

    private class SetupGateInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            if (instanceConfig.isConfigured()) {
                return true;
            }
            response.sendRedirect("/setup");
            return false;
        }
    }

    private class AdminInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            var user = auth.currentUser(request.getSession(false));
            if (user.isEmpty() || !user.get().isUserIsAdmin()) {
                response.sendRedirect("/login");
                return false;
            }
            return true;
        }
    }
}
