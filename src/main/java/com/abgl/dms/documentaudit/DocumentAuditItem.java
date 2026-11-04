package com.abgl.dms.documentaudit;

import java.time.OffsetDateTime;

public record DocumentAuditItem(
        long auditId,
        String actionType,
        String workflowType,
        String workflowCode,
        String actionBy,
        OffsetDateTime actionAt,
        String actionDescription,
        String remarks
) {}
