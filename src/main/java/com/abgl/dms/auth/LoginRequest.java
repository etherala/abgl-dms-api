package com.abgl.dms.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 254) String username,
        @NotBlank @Size(max = 1024) String password,
        @NotBlank @Size(max = 512) String challengeId,
        @NotBlank @Size(max = 100) String captchaAnswer
) {}
