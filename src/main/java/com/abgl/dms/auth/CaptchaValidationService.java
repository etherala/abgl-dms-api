package com.abgl.dms.auth;

import com.abgl.dms.i18n.ApplicationMessages;
import com.abgl.dms.i18n.MessageKeys;
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
    private final ApplicationMessages messages;

    public CaptchaValidationService(CaptchaService captchaService, ApplicationMessages messages) {
        this.captchaService = captchaService;
        this.messages = messages;
    }

    public void validateAndConsume(String challengeId, String captchaAnswer) {
        CaptchaService.ValidateResult result = captchaService.validate(challengeId, captchaAnswer);
        if (CaptchaService.CODE_ERROR.equals(result.code())) {
            throw new IllegalStateException(messages.get(MessageKeys.CAPTCHA_INTERNAL_ERROR));
        }
        if (!result.valid()) {
            log.warn("Login CAPTCHA rejected; result={}", result.code());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get(MessageKeys.CAPTCHA_INVALID));
        }
    }
}
