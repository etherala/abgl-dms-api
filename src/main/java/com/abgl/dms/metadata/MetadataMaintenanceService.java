package com.abgl.dms.metadata;

import com.abgl.dms.cache.AppCacheInitializer;
import com.abgl.dms.repository.MetadataMaintenanceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MetadataMaintenanceService {

    private static final Set<String> RECORD_STATUSES =
            Set.of("I", "P", "A", "R", "D");

    private final MetadataMaintenanceRepository repository;
    private final AppCacheInitializer appCacheInitializer;

    public MetadataMaintenanceService(
            MetadataMaintenanceRepository repository,
            AppCacheInitializer appCacheInitializer) {
        this.repository = repository;
        this.appCacheInitializer = appCacheInitializer;
    }

    public List<String> getMetadataTypes() {
        return repository.findMetadataTypes();
    }

    public MetadataMaintenancePage search(MetadataMaintenanceSearch search) {
        String status = cleanNullable(search.status());
        if (status != null && !RECORD_STATUSES.contains(status.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported metadata status");
        }
        MetadataMaintenanceSearch normalized = new MetadataMaintenanceSearch(
                cleanNullable(search.mdType()),
                cleanNullable(search.mdCode()),
                cleanNullable(search.mdDesc()),
                cleanNullable(search.mdParentType()),
                status == null ? null : status.toUpperCase(),
                search.isActive(),
                cleanNullable(search.createdBy()),
                search.page(),
                search.size()
        );
        int page = search.page() == null ? 0 : search.page();
        int size = search.size() == null ? 25 : search.size();
        return repository.search(normalized, page, size);
    }

    public MetadataMaintenanceRecord getRecord(long id) {
        return repository.findRecord(id);
    }

    public MetadataMaintenanceRecord create(
            MetadataMaintenanceInput input, String username, String ipAddress) {
        return repository.create(normalize(input), username, ipAddress);
    }

    public MetadataMaintenanceRecord createDraft(
            MetadataMaintenanceInput input, String username, String ipAddress) {
        return repository.createDraft(normalize(input), username, ipAddress);
    }

    public MetadataMaintenanceRecord createEditDraft(
            long id, MetadataMaintenanceInput input, String username, String ipAddress) {
        return repository.createEditDraft(id, normalize(input), username, ipAddress);
    }

    public MetadataMaintenanceRecord updateDraft(
            UUID wbId, MetadataMaintenanceInput input, String username, String ipAddress) {
        return repository.updateDraft(wbId, normalize(input), username, ipAddress);
    }

    public MetadataMaintenanceRecord getWorkbench(UUID wbId) {
        return repository.findWorkbench(wbId);
    }

    public MetadataMaintenanceRecord submit(UUID wbId, String username, String ipAddress) {
        return repository.submit(wbId, username, ipAddress);
    }

    public MetadataMaintenanceRecord update(
            long id, MetadataMaintenanceInput input, String username, String ipAddress) {
        return repository.update(id, normalize(input), username, ipAddress);
    }

    public List<MetadataMaintenanceRecord> getPending() {
        return repository.findPending();
    }

    public MetadataMaintenanceRecord approve(UUID wbId, String username, String ipAddress) {
        MetadataMaintenanceRecord record = repository.approve(wbId, username, ipAddress);
        refreshDropList();
        return record;
    }

    public MetadataMaintenanceRecord reject(
            UUID wbId, String username, String reason, String ipAddress) {
        return repository.reject(wbId, username, reason, ipAddress);
    }

    public MetadataMaintenanceRecord delete(long id, String username, String ipAddress) {
        return repository.delete(id, username, ipAddress);
    }

    public MetadataMaintenanceRecord deleteDraft(UUID wbId, String username, String ipAddress) {
        return repository.deleteDraft(wbId, username, ipAddress);
    }

    public List<MetadataMaintenanceAuditItem> getAudit(long id) {
        return repository.findAudit(id);
    }

    public List<MetadataMaintenanceAuditItem> getWorkbenchAudit(UUID wbId) {
        return repository.findWorkbenchAudit(wbId);
    }

    private void refreshDropList() {
        appCacheInitializer.loadMetadataDropList();
    }

    private static MetadataMaintenanceInput normalize(MetadataMaintenanceInput input) {
        return new MetadataMaintenanceInput(
                input.mdType().trim(),
                input.mdCode().trim(),
                input.mdDesc().trim(),
                cleanNullable(input.mdParentType()),
                input.isActive()
        );
    }

    private static String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
