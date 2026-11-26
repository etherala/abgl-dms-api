package com.abgl.dms.vaultmaintenance;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VaultMaintenanceRecord(
        Long vaultId,
        UUID wbId,
        String vaultTrayCode,
        String vaultTrayName,
        String vaultShelf,
        String vaultRack,
        String vaultRoom,
        String vaultBranch,
        String vaultLocation,
        boolean isActive,
        String status,
        String actionType,
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
        Integer version
) {}
