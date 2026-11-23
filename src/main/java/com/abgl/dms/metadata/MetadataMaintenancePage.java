package com.abgl.dms.metadata;

import java.util.List;

public record MetadataMaintenancePage(
        List<MetadataMaintenanceRecord> items,
        long total,
        int page,
        int size
) {}
