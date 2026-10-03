package com.abgl.dms.captcha.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfig.BucketFactory bucketFactory;

    public RateLimitInterceptor(RateLimitConfig.BucketFactory bucketFactory) {
        this.bucketFactory = bucketFactory;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/captcha/")) {
            return true;
        }

        String key = request.getRemoteAddr() + ":" + path;
        if (bucketFactory.tryConsume(key)) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"valid\":false,\"message\":\"rate_limited\"}");
        return false;
    }
}
