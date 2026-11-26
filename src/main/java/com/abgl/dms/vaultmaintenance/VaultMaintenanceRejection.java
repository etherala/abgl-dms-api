package com.abgl.dms.vaultmaintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VaultMaintenanceRejection(@NotBlank @Size(max = 500) String reason) {}
