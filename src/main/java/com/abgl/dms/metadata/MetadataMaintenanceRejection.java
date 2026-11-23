package com.abgl.dms.metadata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MetadataMaintenanceRejection(
        @NotBlank @Size(max = 1000) String reason
) {}
