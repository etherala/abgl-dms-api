package com.abgl.dms.metadata;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MetadataMaintenanceRecord(
        Long id,
        UUID wbId,
        String mdType,
        String mdCode,
        String mdDesc,
        boolean isActive,
        String mdParentType,
        String mdParentCode,
        String status,
        String createdBy,
        OffsetDateTime createdDatetime,
        String submittedBy,
        OffsetDateTime submittedDatetime,
        String approvedBy,
        OffsetDateTime approvedDatetime,
        String rejectedBy,
        OffsetDateTime rejectedDatetime,
        String rejectionReason,
        String updatedBy,
        OffsetDateTime updatedDatetime,
        String actionType,
        Integer version
) {}
