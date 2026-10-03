package com.abgl.dms.captcha.controller;

import com.abgl.dms.captcha.service.CaptchaService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/captcha")
@Validated
public class CaptchaController {

    private final CaptchaService service;

    public CaptchaController(CaptchaService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate() throws Exception {
        var res = service.generate();
        return ResponseEntity.ok(Map.of(
                "id", res.id(),
                "imageBase64", res.imageBase64(),
                "expiresAt", res.expiresAt()
        ));
    }

    public static class ValidateRequest {
        @NotBlank
        public String id;
        @NotBlank
        public String answer;
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody ValidateRequest req) {
        var res = service.validate(req.id, req.answer);
        return ResponseEntity.ok(Map.of(
                "valid", res.valid(),
                "message", res.message()
        ));
    }
}
