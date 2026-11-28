package com.abgl.dms.vaultmaintenance;

import java.util.List;

public record VaultHierarchy(
        List<VaultHierarchyOption> locations,
        List<VaultHierarchyOption> branches,
        List<VaultHierarchyOption> rooms,
        List<VaultHierarchyOption> racks,
        List<VaultHierarchyOption> shelves
) {}
