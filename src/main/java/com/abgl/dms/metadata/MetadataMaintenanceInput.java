package com.abgl.dms.metadata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MetadataMaintenanceInput(
        @NotBlank @Size(max = 100) String mdType,
        @NotBlank @Size(max = 100) String mdCode,
        @NotBlank @Size(max = 255) String mdDesc,
        @Size(max = 100) String mdParentType,
        Boolean isActive
) {}
