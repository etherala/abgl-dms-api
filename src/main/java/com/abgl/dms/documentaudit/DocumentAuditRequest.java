package com.abgl.dms.documentaudit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentAuditRequest(
        @NotNull @Positive Long imageId,
        @NotBlank @Size(max = 30) String wflowType
) {}
