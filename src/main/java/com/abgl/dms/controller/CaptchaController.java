package com.abgl.dms.controller;

import com.abgl.dms.i18n.ApplicationMessages;
import com.abgl.dms.i18n.MessageKeys;
import com.abgl.dms.service.CaptchaService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/captcha")
@Validated
public class CaptchaController {

    private static final Logger log = LoggerFactory.getLogger(CaptchaController.class);

    private final CaptchaService service;
    private final ApplicationMessages messages;

    public CaptchaController(CaptchaService service, ApplicationMessages messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping("/generate")
    public ResponseEntity<?> generate() throws Exception {
        log.debug("Received CAPTCHA generation request");
        try {
            var res = service.generate();
            log.debug("CAPTCHA generated successfully; id={}, expiresAt={}", res.id(), res.expiresAt());
            return ResponseEntity.ok(Map.of(
                    "id", res.id(),
                    "imageBase64", res.imageBase64(),
                    "expiresAt", res.expiresAt()
            ));
        } catch (Exception ex) {
            log.error("CAPTCHA generation failed", ex);
            throw ex;
        }
    }

    public static class ValidateRequest {
        @NotBlank
        public String id;
        @NotBlank
        public String answer;
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody ValidateRequest req) {
        log.debug("Received CAPTCHA validation request");
        var res = service.validate(req.id, req.answer);
        log.debug("CAPTCHA validation completed; valid={}, code={}", res.valid(), res.code());
        return ResponseEntity.ok(Map.of(
                "valid", res.valid(),
                "code", res.code(),
                "message", localizedMessage(res.code())
        ));
    }

    private String localizedMessage(String code) {
        String key = switch (code) {
            case CaptchaService.CODE_NOT_FOUND_OR_EXPIRED -> MessageKeys.CAPTCHA_RESULT_NOT_FOUND;
            case CaptchaService.CODE_EXPIRED -> MessageKeys.CAPTCHA_RESULT_EXPIRED;
            case CaptchaService.CODE_VALID -> MessageKeys.CAPTCHA_RESULT_VALID;
            case CaptchaService.CODE_INVALID -> MessageKeys.CAPTCHA_RESULT_INVALID;
            case CaptchaService.CODE_BAD_ID -> MessageKeys.CAPTCHA_RESULT_BAD_ID;
            default -> MessageKeys.CAPTCHA_INTERNAL_ERROR;
        };
        return messages.get(key);
    }
}
