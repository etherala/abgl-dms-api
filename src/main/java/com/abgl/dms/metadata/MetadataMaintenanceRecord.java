package com.abgl.dms.metadata;

import java.time.OffsetDateTime;

public record MetadataMaintenanceRecord(
        long id,
        String mdType,
        String mdCode,
        String mdDesc,
        boolean isActive,
        String mdParentType,
        String status,
        String createdBy,
        OffsetDateTime createdDatetime,
        String approvedBy,
        OffsetDateTime approvedDatetime,
        String updatedBy,
        OffsetDateTime updatedDatetime
) {}
