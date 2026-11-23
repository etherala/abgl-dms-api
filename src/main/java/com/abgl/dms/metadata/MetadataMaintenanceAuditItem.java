package com.abgl.dms.metadata;

import java.time.OffsetDateTime;

public record MetadataMaintenanceAuditItem(
        long auditId,
        String actionType,
        String workflowType,
        String workflowCode,
        String actionBy,
        OffsetDateTime actionAt,
        String actionDescription,
        String remarks
) {}
