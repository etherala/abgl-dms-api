package com.abgl.dms.vaultmaintenance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record VaultMaintenanceSearch(
        @Size(max = 100) String vaultTrayCode,
        @Size(max = 255) String vaultTrayName,
        @Size(max = 100) String vaultShelf,
        @Size(max = 100) String vaultRack,
        @Size(max = 100) String vaultRoom,
        @Size(max = 100) String vaultBranch,
        @Size(max = 255) String vaultLocation,
        @Size(max = 20) String status,
        Boolean isActive,
        @Size(max = 100) String createdBy,
        @Min(0) Integer page,
        @Min(1) @Max(200) Integer size
) {}
