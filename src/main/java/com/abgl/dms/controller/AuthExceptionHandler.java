package com.abgl.dms.controller;

import com.abgl.dms.i18n.ApplicationMessages;
import com.abgl.dms.i18n.MessageKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);
    private final ApplicationMessages messages;

    public AuthExceptionHandler(ApplicationMessages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationFailure() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", messages.get(MessageKeys.AUTH_INVALID_CREDENTIALS)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalidLoginRequest(MethodArgumentNotValidException exception) {
        log.warn("Login request validation failed; invalidFields={}",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField())
                        .distinct()
                        .toList());
        return ResponseEntity.badRequest().body(Map.of("message", messages.get(MessageKeys.AUTH_INVALID_REQUEST)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableLoginRequest(
            HttpMessageNotReadableException exception) {
        log.warn("Login request body could not be parsed; exceptionType={}",
                exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(Map.of("message", messages.get(MessageKeys.AUTH_INVALID_REQUEST)));
    }
}
