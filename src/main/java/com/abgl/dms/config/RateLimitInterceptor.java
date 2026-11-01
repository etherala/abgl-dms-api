package com.abgl.dms.config;

import com.abgl.dms.i18n.ApplicationMessages;
import com.abgl.dms.i18n.MessageKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfig.BucketFactory bucketFactory;
    private final ApplicationMessages messages;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(
            RateLimitConfig.BucketFactory bucketFactory,
            ApplicationMessages messages,
            ObjectMapper objectMapper) {
        this.bucketFactory = bucketFactory;
        this.messages = messages;
        this.objectMapper = objectMapper;
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
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "valid", false,
                "code", "rate_limited",
                "message", messages.get(MessageKeys.RATE_LIMIT_EXCEEDED)));
        return false;
    }
}
