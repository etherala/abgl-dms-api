package com.abgl.dms.controller;

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

    public CaptchaController(CaptchaService service) {
        this.service = service;
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
        log.debug("CAPTCHA validation completed; valid={}, message={}", res.valid(), res.message());
        return ResponseEntity.ok(Map.of(
                "valid", res.valid(),
                "message", res.message()
        ));
    }
}
