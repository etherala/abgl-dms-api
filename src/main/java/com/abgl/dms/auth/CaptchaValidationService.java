package com.abgl.dms.auth;

import com.abgl.dms.service.CaptchaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaptchaValidationService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaValidationService.class);

    private final CaptchaService captchaService;

    public CaptchaValidationService(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    public void validateAndConsume(String challengeId, String captchaAnswer) {
        CaptchaService.ValidateResult result = captchaService.validate(challengeId, captchaAnswer);
        if ("error".equals(result.message())) {
            throw new IllegalStateException("CAPTCHA validation failed internally");
        }
        if (!result.valid()) {
            log.warn("Login CAPTCHA rejected; result={}", result.message());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired CAPTCHA");
        }
    }
}
