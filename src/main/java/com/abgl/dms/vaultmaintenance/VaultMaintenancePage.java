package com.abgl.dms.vaultmaintenance;

import java.util.List;

public record VaultMaintenancePage(List<VaultMaintenanceRecord> items, long total, int page, int size) {}
