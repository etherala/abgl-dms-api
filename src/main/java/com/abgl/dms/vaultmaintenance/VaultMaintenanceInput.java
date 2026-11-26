package com.abgl.dms.vaultmaintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VaultMaintenanceInput(
        @NotBlank @Size(max = 100) String vaultTrayCode,
        @NotBlank @Size(max = 255) String vaultTrayName,
        @NotBlank @Size(max = 100) String vaultShelf,
        @NotBlank @Size(max = 100) String vaultRack,
        @NotBlank @Size(max = 100) String vaultRoom,
        @NotBlank @Size(max = 100) String vaultBranch,
        @NotBlank @Size(max = 255) String vaultLocation,
        @NotNull Boolean isActive
) {}
