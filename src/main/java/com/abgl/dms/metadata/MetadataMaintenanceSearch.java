package com.abgl.dms.metadata;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record MetadataMaintenanceSearch(
        @Size(max = 100) String mdType,
        @Size(max = 100) String mdCode,
        @Size(max = 255) String mdDesc,
        @Size(max = 100) String mdParentType,
        @Size(max = 100) String mdParentCode,
        @Size(max = 20) String status,
        Boolean isActive,
        @Size(max = 100) String createdBy,
        @Min(0) Integer page,
        @Min(1) @jakarta.validation.constraints.Max(200) Integer size
) {}
