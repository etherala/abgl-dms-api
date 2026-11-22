package com.abgl.dms.controller;

import com.abgl.dms.i18n.ApplicationMessages;
import com.abgl.dms.i18n.MessageKeys;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class ApiErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);
    private final ApplicationMessages messages;

    public ApiErrorController(ApplicationMessages messages) {
        this.messages = messages;
    }

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer value ? value : 500;
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = requestUri instanceof String uri ? uri : request.getRequestURI();
        Object exceptionAttribute = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (exceptionAttribute instanceof Throwable exception) {
            log.error("Unhandled request error; status={}, path={}", status, path, exception);
        } else {
            log.warn("Request failed; status={}, path={}", status, path);
        }

        HttpStatusCode statusCode = HttpStatusCode.valueOf(status);
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status,
                "error", statusCode.toString(),
                "message", messages.get(MessageKeys.REQUEST_FAILED),
                "path", path
        );
        return ResponseEntity.status(statusCode).body(body);
    }
}
