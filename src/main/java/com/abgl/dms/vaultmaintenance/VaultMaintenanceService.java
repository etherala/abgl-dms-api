package com.abgl.dms.vaultmaintenance;

import com.abgl.dms.repository.VaultMaintenanceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class VaultMaintenanceService {

    private static final Set<String> STATUSES = Set.of("I", "P", "A", "R", "D");

    private final VaultMaintenanceRepository repository;

    public VaultMaintenanceService(VaultMaintenanceRepository repository) {
        this.repository = repository;
    }

    public VaultMaintenancePage search(VaultMaintenanceSearch search) {
        String status = cleanNullable(search.status());
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                throw new IllegalArgumentException("Unsupported vault status");
            }
        }
        VaultMaintenanceSearch normalized = new VaultMaintenanceSearch(
                cleanNullable(search.vaultTrayCode()),
                cleanNullable(search.vaultTrayName()),
                cleanNullable(search.vaultShelf()),
                cleanNullable(search.vaultRack()),
                cleanNullable(search.vaultRoom()),
                cleanNullable(search.vaultBranch()),
                cleanNullable(search.vaultLocation()),
                status,
                search.isActive(),
                cleanNullable(search.createdBy()),
                search.page(),
                search.size()
        );
        int page = search.page() == null ? 0 : search.page();
        int size = search.size() == null ? 25 : search.size();
        return repository.search(normalized, page, size);
    }

    public VaultMaintenanceRecord getRecord(long vaultId) {
        return repository.findMain(vaultId);
    }

    public VaultMaintenanceRecord getWorkbench(UUID wbId) {
        return repository.findWorkbench(wbId);
    }

    public VaultHierarchy getHierarchy(
            String locationCode, String branchCode, String roomCode, String rackCode) {
        return repository.findHierarchy(
                cleanNullable(locationCode), cleanNullable(branchCode),
                cleanNullable(roomCode), cleanNullable(rackCode));
    }

    public List<VaultHierarchyOption> getHierarchyChildren(
            String parentType, String parentCode, String childType) {
        String normalizedParentType = cleanNullable(parentType);
        String normalizedParentCode = cleanNullable(parentCode);
        String normalizedChildType = cleanNullable(childType);
        if (normalizedParentCode == null) {
            throw new IllegalArgumentException("parentCode is required");
        }
        String expectedChildType = switch (normalizedParentType == null ? "" : normalizedParentType) {
            case "VAULT_LOCATION" -> "VAULT_BRANCH";
            case "VAULT_BRANCH" -> "VAULT_ROOM";
            case "VAULT_ROOM" -> "VAULT_RACK";
            case "VAULT_RACK" -> "VAULT_SHELF";
            default -> throw new IllegalArgumentException("Unsupported vault parent type");
        };
        if (!expectedChildType.equals(normalizedChildType)) {
            throw new IllegalArgumentException("Unsupported vault parent/child type combination");
        }
        return repository.findHierarchyChildren(
                normalizedParentType, normalizedParentCode, normalizedChildType);
    }

    public VaultMaintenanceRecord create(
            VaultMaintenanceInput input, String username, String ipAddress) {
        return repository.create(normalize(input), username, ipAddress);
    }

    public VaultMaintenanceRecord createDraft(
            VaultMaintenanceInput input, String username, String ipAddress) {
        return repository.createDraft(normalize(input), username, ipAddress);
    }

    public VaultMaintenanceRecord createEditDraft(
            long vaultId, VaultMaintenanceInput input, String username, String ipAddress) {
        return repository.createEditDraft(vaultId, normalize(input), username, ipAddress);
    }

    public VaultMaintenanceRecord updateDraft(
            UUID wbId, VaultMaintenanceInput input, String username, String ipAddress) {
        return repository.updateDraft(wbId, normalize(input), username, ipAddress);
    }

    public VaultMaintenanceRecord submit(UUID wbId, String username, String ipAddress) {
        return repository.submit(wbId, username, ipAddress);
    }

    public VaultMaintenanceRecord update(
            long vaultId, VaultMaintenanceInput input, String username, String ipAddress) {
        return repository.update(vaultId, normalize(input), username, ipAddress);
    }

    public List<VaultMaintenanceRecord> getPending() {
        return repository.findPending();
    }

    public VaultMaintenanceRecord approve(UUID wbId, String username, String ipAddress) {
        return repository.approve(wbId, username, ipAddress);
    }

    public VaultMaintenanceRecord reject(
            UUID wbId, String username, String reason, String ipAddress) {
        return repository.reject(wbId, username, reason.trim(), ipAddress);
    }

    public VaultMaintenanceRecord deleteDraft(UUID wbId, String username, String ipAddress) {
        return repository.deleteDraft(wbId, username, ipAddress);
    }

    public List<VaultMaintenanceAuditItem> getAudit(long vaultId) {
        return repository.findAudit(vaultId);
    }

    public List<VaultMaintenanceAuditItem> getWorkbenchAudit(UUID wbId) {
        return repository.findWorkbenchAudit(wbId);
    }

    private static VaultMaintenanceInput normalize(VaultMaintenanceInput input) {
        return new VaultMaintenanceInput(
                input.vaultTrayCode().trim(),
                input.vaultTrayName().trim(),
                input.vaultShelf().trim(),
                input.vaultRack().trim(),
                input.vaultRoom().trim(),
                input.vaultBranch().trim(),
                input.vaultLocation().trim(),
                input.isActive()
        );
    }

    private static String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
