package com.abgl.dms.vaultmaintenance;

import java.time.OffsetDateTime;

public record VaultMaintenanceAuditItem(
        long auditId,
        String actionType,
        String workflowType,
        String workflowCode,
        String actionBy,
        OffsetDateTime actionAt,
        String actionDescription,
        String remarks
) {}
