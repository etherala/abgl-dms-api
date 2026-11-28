package com.abgl.dms.repository;

import com.abgl.dms.vaultmaintenance.VaultMaintenanceAuditItem;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceInput;
import com.abgl.dms.vaultmaintenance.VaultMaintenancePage;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceRecord;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceSearch;
import com.abgl.dms.vaultmaintenance.VaultHierarchy;
import com.abgl.dms.vaultmaintenance.VaultHierarchyOption;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Repository
public class VaultMaintenanceRepository {

    private static final String WORKFLOW_TYPE = "VAULT_STORAGE";
    private static final String MAIN_COLUMNS = """
            vault_id, NULL::uuid AS wb_id, vault_tray_code, vault_tray_name,
            vault_shelf, vault_rack, vault_room, vault_branch, vault_location,
            is_active, status, NULL::varchar AS action_type, created_by, created_datetime,
            submitted_by, submitted_datetime, approved_by, approved_datetime,
            NULL::varchar AS rejected_by, NULL::timestamptz AS rejected_datetime,
            NULL::varchar AS rejection_reason, updated_by, updated_datetime, version
            """;
    private static final String WORKBENCH_COLUMNS = """
            vault_id, wb_id, vault_tray_code, vault_tray_name, vault_shelf, vault_rack,
            vault_room, vault_branch, vault_location, is_active, status, action_type,
            created_by, created_datetime, submitted_by, submitted_datetime,
            approved_by, approved_datetime, rejected_by, rejected_datetime,
            rejection_reason, updated_by, updated_datetime, version
            """;

    private final JdbcTemplate jdbcTemplate;

    public VaultMaintenanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public VaultMaintenancePage search(VaultMaintenanceSearch search, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();
        addLikeFilter(where, parameters, "vault_tray_code", search.vaultTrayCode());
        addLikeFilter(where, parameters, "vault_tray_name", search.vaultTrayName());
        addLikeFilter(where, parameters, "vault_shelf", search.vaultShelf());
        addLikeFilter(where, parameters, "vault_rack", search.vaultRack());
        addLikeFilter(where, parameters, "vault_room", search.vaultRoom());
        addLikeFilter(where, parameters, "vault_branch", search.vaultBranch());
        addLikeFilter(where, parameters, "vault_location", search.vaultLocation());
        addLikeFilter(where, parameters, "created_by", search.createdBy());
        if (search.status() != null && !search.status().isBlank()) {
            where.append(" AND status = ?");
            parameters.add(search.status());
        }
        if (search.isActive() != null) {
            where.append(" AND is_active = ?");
            parameters.add(search.isActive());
        }

        String records = searchableRecords();
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + records + ") vault_records" + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<VaultMaintenanceRecord> items = jdbcTemplate.query(
                "SELECT * FROM (" + records + ") vault_records" + where
                        + " ORDER BY created_datetime DESC, vault_id DESC, wb_id DESC NULLS LAST LIMIT ? OFFSET ?",
                this::mapRecord, pageParameters.toArray());
        return new VaultMaintenancePage(items, total == null ? 0 : total, page, size);
    }

    public VaultMaintenanceRecord findMain(long vaultId) {
        List<VaultMaintenanceRecord> records = jdbcTemplate.query(
                "SELECT " + MAIN_COLUMNS + " FROM dms.vault_storage_mst WHERE vault_id = ?",
                this::mapRecord, vaultId);
        if (records.isEmpty()) {
            throw new NoSuchElementException("Vault record not found");
        }
        return records.getFirst();
    }

    public VaultMaintenanceRecord findWorkbench(UUID wbId) {
        return findWorkbench(wbId, false);
    }

    public List<VaultMaintenanceRecord> findPending() {
        return jdbcTemplate.query("""
                SELECT vault_id, wb_id, vault_tray_code, vault_tray_name, vault_shelf,
                       vault_rack, vault_room, vault_branch, vault_location, is_active,
                       status, action_type, created_by, created_datetime, submitted_by,
                       submitted_datetime, approved_by, approved_datetime, rejected_by,
                       rejected_datetime, rejection_reason, updated_by, updated_datetime, version
                FROM dms.vault_storage_wb
                WHERE status = 'P'
                ORDER BY submitted_datetime, wb_id
                """, this::mapRecord);
    }

    public VaultHierarchy findHierarchy(
            String locationCode, String branchCode, String roomCode, String rackCode) {
        return new VaultHierarchy(
                findLocations(),
                locationCode == null ? List.of() : findChildren("VAULT_LOCATION", locationCode, "VAULT_BRANCH"),
                branchCode == null ? List.of() : findChildren("VAULT_BRANCH", branchCode, "VAULT_ROOM"),
                roomCode == null ? List.of() : findChildren("VAULT_ROOM", roomCode, "VAULT_RACK"),
                rackCode == null ? List.of() : findChildren("VAULT_RACK", rackCode, "VAULT_SHELF"));
    }

    public List<VaultHierarchyOption> findHierarchyChildren(
            String parentType, String parentCode, String childType) {
        return findChildren(parentType, parentCode, childType);
    }

    @Transactional
    public VaultMaintenanceRecord create(VaultMaintenanceInput input, String username, String ipAddress) {
        return createRequest(input, "I", "P", 1, null, username, username, ipAddress);
    }

    @Transactional
    public VaultMaintenanceRecord createDraft(
            VaultMaintenanceInput input, String username, String ipAddress) {
        return createRequest(input, "I", "I", 1, null, username, null, ipAddress);
    }

    @Transactional
    public VaultMaintenanceRecord createEditDraft(
            long vaultId, VaultMaintenanceInput input, String username, String ipAddress) {
        VaultMaintenanceRecord initial = findMain(vaultId);
        requireUnchangedTrayCode(initial, input);
        VaultMaintenanceRecord existing = findWorkbenchByTray(initial.vaultTrayCode(), true);
        VaultMaintenanceRecord approved = findMainForUpdate(vaultId);
        requireApproved(approved);
        requireUnchangedTrayCode(approved, input);
        int nextVersion = nextVersion(approved);
        validateDropdowns(input);
        return saveOrReuseRequest(
                existing, input, "U", "I", nextVersion, approved.vaultId(), username, null, ipAddress);
    }

    @Transactional
    public VaultMaintenanceRecord updateDraft(
            UUID wbId, VaultMaintenanceInput input, String username, String ipAddress) {
        VaultMaintenanceRecord before = findWorkbenchForUpdate(wbId);
        requireEditableWorkbench(before);
        validateDropdowns(input);
        int version = validateAndGetRequestVersion(before.actionType(), before.vaultId(),
                before.vaultTrayCode(), input);
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE dms.vault_storage_wb
                    SET vault_tray_code = ?, vault_tray_name = ?, vault_shelf = ?, vault_rack = ?, vault_room = ?,
                        vault_branch = ?, vault_location = ?, is_active = ?, status = 'I',
                        submitted_by = NULL, submitted_datetime = NULL,
                        rejected_by = NULL, rejected_datetime = NULL, rejection_reason = NULL,
                        approved_by = NULL, approved_datetime = NULL,
                        updated_by = ?, updated_datetime = CURRENT_TIMESTAMP, version = ?
                    WHERE wb_id = ? AND status IN ('I', 'R', 'D')
                    """, clean(input.vaultTrayCode()), clean(input.vaultTrayName()),
                    clean(input.vaultShelf()), clean(input.vaultRack()), clean(input.vaultRoom()),
                    clean(input.vaultBranch()), clean(input.vaultLocation()), input.isActive(),
                    username, version, wbId);
            requireOneRow(updated, "Vault draft was not saved");
        } catch (DuplicateKeyException exception) {
            throw duplicateTrayCode(exception);
        }
        insertAudit("U", workbenchAuditCode(wbId), username,
                "Saved vault draft", "Vault workbench record saved as a draft.", ipAddress);
        return findWorkbench(wbId);
    }

    @Transactional
    public VaultMaintenanceRecord submit(UUID wbId, String username, String ipAddress) {
        VaultMaintenanceRecord request = findWorkbenchForUpdate(wbId);
        requireEditableWorkbench(request);
        validateDropdowns(toInput(request));
        int expectedVersion = validateAndGetRequestVersion(
                request.actionType(), request.vaultId(), request.vaultTrayCode(), toInput(request));
        if (request.version() == null || request.version() != expectedVersion) {
            throw new IllegalStateException(
                    "Vault record changed after this request was created; save a new edit draft");
        }
        int updated = jdbcTemplate.update("""
                UPDATE dms.vault_storage_wb
                SET status = 'P', submitted_by = ?, submitted_datetime = CURRENT_TIMESTAMP,
                    rejected_by = NULL, rejected_datetime = NULL, rejection_reason = NULL,
                    approved_by = NULL, approved_datetime = NULL,
                    updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                WHERE wb_id = ? AND status IN ('I', 'R', 'D')
                """, username, username, wbId);
        requireOneRow(updated, "Vault request was not submitted");
        insertAudit("P", workbenchAuditCode(wbId), username,
                "Submitted vault request", "Vault record submitted for approval.", ipAddress);
        return findWorkbench(wbId);
    }

    @Transactional
    public VaultMaintenanceRecord update(
            long vaultId, VaultMaintenanceInput input, String username, String ipAddress) {
        VaultMaintenanceRecord initial = findMain(vaultId);
        requireUnchangedTrayCode(initial, input);
        VaultMaintenanceRecord existing = findWorkbenchByTray(initial.vaultTrayCode(), true);
        VaultMaintenanceRecord approved = findMainForUpdate(vaultId);
        requireApproved(approved);
        requireUnchangedTrayCode(approved, input);
        validateDropdowns(input);
        int nextVersion = nextVersion(approved);
        return saveOrReuseRequest(
                existing, input, "U", "P", nextVersion, approved.vaultId(), username, username, ipAddress);
    }

    @Transactional
    public VaultMaintenanceRecord approve(UUID wbId, String username, String ipAddress) {
        VaultMaintenanceRecord request = findWorkbenchForUpdate(wbId);
        requirePending(request);
        requireDifferentReviewer(request, username);
        if (!isValidHierarchy(toInput(request))) {
            throw new IllegalStateException(
                    "Vault hierarchy changed after this request was submitted; submit a new request");
        }

        long vaultId;
        if ("I".equals(request.actionType())) {
            ensureNoMainRecord(request.vaultTrayCode());
            vaultId = insertApprovedRecord(request, username);
        } else if ("U".equals(request.actionType())) {
            VaultMaintenanceRecord current = findMainByTrayForUpdate(request.vaultTrayCode());
            requireApproved(current);
            if (!request.vaultId().equals(current.vaultId())) {
                throw new IllegalStateException("Vault request no longer refers to the current record");
            }
            requireExpectedVersion(request, current);
            archiveApprovedRecord(current, username);
            updateMainRecord(request, current, username);
            vaultId = current.vaultId();
        } else {
            throw new IllegalStateException("Unsupported vault action type");
        }

        int removed = jdbcTemplate.update(
                "DELETE FROM dms.vault_storage_wb WHERE wb_id = ? AND status = 'P'", wbId);
        requireOneRow(removed, "Approved vault request was not removed from workbench");
        insertAudit("A", Long.toString(vaultId), username,
                "Approved vault record",
                "Approved workbench request: " + workbenchAuditCode(wbId), ipAddress);
        return findMain(vaultId);
    }

    @Transactional
    public VaultMaintenanceRecord reject(
            UUID wbId, String username, String reason, String ipAddress) {
        VaultMaintenanceRecord request = findWorkbenchForUpdate(wbId);
        requirePending(request);
        requireDifferentReviewer(request, username);
        int updated = jdbcTemplate.update("""
                UPDATE dms.vault_storage_wb
                SET status = 'R', rejected_by = ?, rejected_datetime = CURRENT_TIMESTAMP,
                    rejection_reason = ?, updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                WHERE wb_id = ? AND status = 'P'
                """, username, clean(reason), username, wbId);
        requireOneRow(updated, "Vault request was not rejected");
        insertAudit("R", workbenchAuditCode(wbId), username,
                "Rejected vault request", clean(reason), ipAddress);
        return findWorkbench(wbId);
    }

    @Transactional
    public VaultMaintenanceRecord deleteDraft(UUID wbId, String username, String ipAddress) {
        VaultMaintenanceRecord request = findWorkbenchForUpdate(wbId);
        if (!"I".equals(request.status()) && !"R".equals(request.status())) {
            throw new IllegalStateException("Only a draft or rejected vault request can be closed");
        }
        int updated = jdbcTemplate.update("""
                UPDATE dms.vault_storage_wb
                SET status = 'D', updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                WHERE wb_id = ? AND status IN ('I', 'R')
                """, username, wbId);
        requireOneRow(updated, "Vault request was not closed");
        insertAudit("D", workbenchAuditCode(wbId), username,
                "Closed vault draft", "Vault workbench request marked as deleted.", ipAddress);
        return findWorkbench(wbId);
    }

    public List<VaultMaintenanceAuditItem> findAudit(long vaultId) {
        findMain(vaultId);
        String workflowCode = Long.toString(vaultId);
        return jdbcTemplate.query("""
                SELECT audit_id, action_type, wflow_type, wflow_code, action_by,
                       action_at, action_description, remarks
                FROM dms.app_audit
                WHERE wflow_type = ?
                  AND (
                      wflow_code = ?
                      OR wflow_code IN (
                          SELECT substring(remarks FROM '(WB:[[:alnum:]-]+)')
                          FROM dms.app_audit
                          WHERE wflow_type = ? AND wflow_code = ?
                            AND action_type = 'A'
                            AND action_description = 'Approved vault record'
                      )
                  )
                ORDER BY action_at DESC, audit_id DESC
                """, this::mapAuditItem, WORKFLOW_TYPE, workflowCode, WORKFLOW_TYPE, workflowCode);
    }

    public List<VaultMaintenanceAuditItem> findWorkbenchAudit(UUID wbId) {
        String workflowCode = workbenchAuditCode(wbId);
        List<VaultMaintenanceAuditItem> items = jdbcTemplate.query("""
                SELECT audit_id, action_type, wflow_type, wflow_code, action_by,
                       action_at, action_description, remarks
                FROM dms.app_audit
                WHERE wflow_type = ?
                  AND (
                      wflow_code = ?
                      OR (
                          action_type = 'A'
                          AND action_description = 'Approved vault record'
                          AND remarks = ?
                      )
                  )
                ORDER BY action_at DESC, audit_id DESC
                """, this::mapAuditItem, WORKFLOW_TYPE, workflowCode,
                "Approved workbench request: " + workflowCode);
        if (items.isEmpty()) {
            Boolean exists = jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM dms.vault_storage_wb WHERE wb_id = ?
                    )
                    """, Boolean.class, wbId);
            if (!Boolean.TRUE.equals(exists)) {
                throw new NoSuchElementException("Vault workbench request not found");
            }
        }
        return items;
    }

    private VaultMaintenanceRecord createRequest(
            VaultMaintenanceInput input, String action, String status, int version, Long vaultId,
            String username, String submitter, String ipAddress) {
        validateDropdowns(input);
        VaultMaintenanceRecord existing = findWorkbenchByTray(input.vaultTrayCode(), true);
        if ("I".equals(action)) {
            ensureNoMainRecord(input.vaultTrayCode());
        }
        return saveOrReuseRequest(
                existing, input, action, status, version, vaultId, username, submitter, ipAddress);
    }

    private VaultMaintenanceRecord saveOrReuseRequest(
            VaultMaintenanceRecord existing, VaultMaintenanceInput input, String action, String status,
            int version, Long vaultId, String username, String submitter, String ipAddress) {
        if (existing != null) {
            if ("P".equals(existing.status())) {
                throw new IllegalStateException(
                        "A pending vault request already exists for this tray code");
            }
            if ("I".equals(existing.status()) && "I".equals(status)) {
                throw new IllegalStateException(
                        "A vault draft already exists for this tray code");
            }
            if (!List.of("I", "R", "D").contains(existing.status())) {
                throw new IllegalStateException("Vault workbench request cannot be reused");
            }
            resetWorkbenchRequest(existing.wbId(), input, action, status, version,
                    vaultId, username, submitter);
            insertAudit(action, workbenchAuditCode(existing.wbId()), username,
                    "Saved vault workbench record", "Vault record saved to workbench.", ipAddress);
            if ("P".equals(status)) {
                insertAudit("P", workbenchAuditCode(existing.wbId()), username,
                        "Submitted vault request", "Vault record submitted for approval.", ipAddress);
            }
            return findWorkbench(existing.wbId());
        }

        try {
            WorkbenchIdentity identity = insertWorkbench(
                    input, action, status, version, vaultId, username, submitter);
            insertAudit(action, workbenchAuditCode(identity.wbId()), username,
                    "Saved vault workbench record", "Vault record saved to workbench.", ipAddress);
            if ("P".equals(status)) {
                insertAudit("P", workbenchAuditCode(identity.wbId()), username,
                        "Submitted vault request", "Vault record submitted for approval.", ipAddress);
            }
            return findWorkbench(identity.wbId());
        } catch (DuplicateKeyException exception) {
            throw duplicateTrayCode(exception);
        }
    }

    private WorkbenchIdentity insertWorkbench(
            VaultMaintenanceInput input, String action, String status, int version, Long vaultId,
            String username, String submitter) {
        String submittedValues = "P".equals(status) ? "?, CURRENT_TIMESTAMP" : "NULL, NULL";
        String sql;
        List<Object> parameters = new ArrayList<>();
        if (vaultId == null) {
            sql = """
                    INSERT INTO dms.vault_storage_wb (
                        vault_tray_code, vault_tray_name, vault_shelf, vault_rack, vault_room,
                        vault_branch, vault_location, is_active, action_type, status, created_by,
                        created_datetime, submitted_by, submitted_datetime, updated_by,
                        updated_datetime, version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, %s,
                            ?, CURRENT_TIMESTAMP, ?)
                    RETURNING wb_id, vault_id
                    """.formatted(submittedValues);
        } else {
            sql = """
                    INSERT INTO dms.vault_storage_wb (
                        vault_id, vault_tray_code, vault_tray_name, vault_shelf, vault_rack,
                        vault_room, vault_branch, vault_location, is_active, action_type, status,
                        created_by, created_datetime, submitted_by, submitted_datetime, updated_by,
                        updated_datetime, version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, %s,
                            ?, CURRENT_TIMESTAMP, ?)
                    RETURNING wb_id, vault_id
                    """.formatted(submittedValues);
            parameters.add(vaultId);
        }
        parameters.addAll(List.of(
                clean(input.vaultTrayCode()), clean(input.vaultTrayName()), clean(input.vaultShelf()),
                clean(input.vaultRack()), clean(input.vaultRoom()), clean(input.vaultBranch()),
                clean(input.vaultLocation()), input.isActive(), action, status, username));
        if ("P".equals(status)) {
            parameters.add(submitter);
        }
        parameters.add(username);
        parameters.add(version);
        return jdbcTemplate.queryForObject(sql, (rs, row) ->
                new WorkbenchIdentity(rs.getObject("wb_id", UUID.class), rs.getLong("vault_id")),
                parameters.toArray());
    }

    private void resetWorkbenchRequest(
            UUID wbId, VaultMaintenanceInput input, String action, String status, int version,
            Long vaultId, String username, String submitter) {
        String submittedSet = "P".equals(status)
                ? "submitted_by = ?, submitted_datetime = CURRENT_TIMESTAMP,"
                : "submitted_by = NULL, submitted_datetime = NULL,";
        List<Object> parameters = new ArrayList<>();
        parameters.add(vaultId);
        parameters.add(clean(input.vaultTrayName()));
        parameters.add(clean(input.vaultShelf()));
        parameters.add(clean(input.vaultRack()));
        parameters.add(clean(input.vaultRoom()));
        parameters.add(clean(input.vaultBranch()));
        parameters.add(clean(input.vaultLocation()));
        parameters.add(input.isActive());
        parameters.add(action);
        parameters.add(status);
        parameters.add(username);
        if ("P".equals(status)) {
            parameters.add(submitter);
        }
        parameters.add(username);
        parameters.add(version);
        parameters.add(wbId);
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE dms.vault_storage_wb
                    SET vault_id = COALESCE(?, vault_id), vault_tray_name = ?,
                        vault_shelf = ?, vault_rack = ?, vault_room = ?, vault_branch = ?,
                        vault_location = ?, is_active = ?, action_type = ?, status = ?,
                        created_by = ?, created_datetime = CURRENT_TIMESTAMP,
                        %s
                        rejected_by = NULL, rejected_datetime = NULL, rejection_reason = NULL,
                        approved_by = NULL, approved_datetime = NULL, updated_by = ?,
                        updated_datetime = CURRENT_TIMESTAMP, version = ?
                    WHERE wb_id = ? AND status IN ('I', 'R', 'D')
                    """.formatted(submittedSet), parameters.toArray());
            requireOneRow(updated, "Vault workbench request cannot be reused");
        } catch (DuplicateKeyException exception) {
            throw duplicateTrayCode(exception);
        }
    }

    private void validateDropdowns(VaultMaintenanceInput input) {
        if (!isValidHierarchy(input)) {
            throw new IllegalArgumentException(
                    "One or more vault dropdown values are invalid, inactive, or do not match the selected hierarchy");
        }
    }

    private boolean isValidHierarchy(VaultMaintenanceInput input) {
        Boolean valid = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM dms.app_metadata_droplist location
                    JOIN dms.app_metadata_droplist branch
                      ON branch.md_type = 'VAULT_BRANCH'
                     AND branch.md_parent_type = 'VAULT_LOCATION'
                     AND branch.md_parent_code = location.md_code
                     AND branch.status = 'A' AND branch.is_active = TRUE
                    JOIN dms.app_metadata_droplist room
                      ON room.md_type = 'VAULT_ROOM'
                     AND room.md_parent_type = 'VAULT_BRANCH'
                     AND room.md_parent_code = branch.md_code
                     AND room.status = 'A' AND room.is_active = TRUE
                    JOIN dms.app_metadata_droplist rack
                      ON rack.md_type = 'VAULT_RACK'
                     AND rack.md_parent_type = 'VAULT_ROOM'
                     AND rack.md_parent_code = room.md_code
                     AND rack.status = 'A' AND rack.is_active = TRUE
                    JOIN dms.app_metadata_droplist shelf
                      ON shelf.md_type = 'VAULT_SHELF'
                     AND shelf.md_parent_type = 'VAULT_RACK'
                     AND shelf.md_parent_code = rack.md_code
                     AND shelf.status = 'A' AND shelf.is_active = TRUE
                    WHERE location.md_type = 'VAULT_LOCATION'
                      AND location.md_parent_type IS NULL
                      AND location.md_parent_code IS NULL
                      AND location.md_code = ?
                      AND location.status = 'A' AND location.is_active = TRUE
                      AND branch.md_code = ?
                      AND room.md_code = ?
                      AND rack.md_code = ?
                      AND shelf.md_code = ?
                )
                """, Boolean.class, input.vaultLocation(), input.vaultBranch(),
                input.vaultRoom(), input.vaultRack(), input.vaultShelf());
        return Boolean.TRUE.equals(valid);
    }

    private List<VaultHierarchyOption> findLocations() {
        return jdbcTemplate.query("""
                SELECT md_code, md_desc
                FROM dms.app_metadata_droplist
                WHERE md_type = 'VAULT_LOCATION'
                  AND md_parent_type IS NULL
                  AND md_parent_code IS NULL
                  AND status = 'A' AND is_active = TRUE
                ORDER BY md_desc, md_code
                """, (rs, row) -> new VaultHierarchyOption(
                rs.getString("md_code"), rs.getString("md_desc")));
    }

    private List<VaultHierarchyOption> findChildren(
            String parentType, String parentCode, String childType) {
        return jdbcTemplate.query("""
                SELECT md_code, md_desc
                FROM dms.app_metadata_droplist
                WHERE md_type = ?
                  AND md_parent_type = ?
                  AND md_parent_code = ?
                  AND status = 'A' AND is_active = TRUE
                ORDER BY md_desc, md_code
                """, (rs, row) -> new VaultHierarchyOption(
                rs.getString("md_code"), rs.getString("md_desc")),
                childType, parentType, parentCode);
    }

    private int validateAndGetRequestVersion(
            String action, long vaultId, String trayCode, VaultMaintenanceInput input) {
        if ("I".equals(action)) {
            ensureNoMainRecord(trayCode);
            return 1;
        }
        if (!"U".equals(action)) {
            throw new IllegalStateException("Unsupported vault action type");
        }
        VaultMaintenanceRecord current = findMainByTrayForUpdate(trayCode);
        requireApproved(current);
        if (current.vaultId() != vaultId) {
            throw new IllegalStateException("Vault request no longer refers to the current record");
        }
        requireUnchangedTrayCode(current, input);
        return nextVersion(current);
    }

    private void ensureNoMainRecord(String trayCode) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM dms.vault_storage_mst WHERE vault_tray_code = ?
                )
                """, Boolean.class, trayCode);
        if (Boolean.TRUE.equals(exists)) {
            throw new IllegalStateException("A vault record already exists for this tray code");
        }
    }

    private VaultMaintenanceRecord findMain(long vaultId, boolean forUpdate) {
        String sql = "SELECT " + MAIN_COLUMNS
                + " FROM dms.vault_storage_mst WHERE vault_id = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        List<VaultMaintenanceRecord> records = jdbcTemplate.query(sql, this::mapRecord, vaultId);
        if (records.isEmpty()) {
            throw new NoSuchElementException("Vault record not found");
        }
        return records.getFirst();
    }

    private VaultMaintenanceRecord findMainForUpdate(long vaultId) {
        return findMain(vaultId, true);
    }

    private VaultMaintenanceRecord findMainByTrayForUpdate(String trayCode) {
        String sql = "SELECT " + MAIN_COLUMNS
                + " FROM dms.vault_storage_mst WHERE vault_tray_code = ? FOR UPDATE";
        List<VaultMaintenanceRecord> records = jdbcTemplate.query(sql, this::mapRecord, trayCode);
        return records.isEmpty() ? null : records.getFirst();
    }

    private VaultMaintenanceRecord findWorkbench(UUID wbId, boolean forUpdate) {
        String sql = "SELECT " + WORKBENCH_COLUMNS
                + " FROM dms.vault_storage_wb WHERE wb_id = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        List<VaultMaintenanceRecord> records = jdbcTemplate.query(sql, this::mapRecord, wbId);
        if (records.isEmpty()) {
            throw new NoSuchElementException("Vault workbench request not found");
        }
        return records.getFirst();
    }

    private VaultMaintenanceRecord findWorkbenchForUpdate(UUID wbId) {
        return findWorkbench(wbId, true);
    }

    private VaultMaintenanceRecord findWorkbenchByTray(String trayCode, boolean forUpdate) {
        String sql = "SELECT " + WORKBENCH_COLUMNS
                + " FROM dms.vault_storage_wb WHERE vault_tray_code = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        List<VaultMaintenanceRecord> records = jdbcTemplate.query(sql, this::mapRecord, trayCode);
        return records.isEmpty() ? null : records.getFirst();
    }

    private long insertApprovedRecord(VaultMaintenanceRecord request, String approver) {
        Long vaultId = jdbcTemplate.queryForObject("""
                INSERT INTO dms.vault_storage_mst (
                    vault_id, vault_tray_code, vault_tray_name, vault_shelf, vault_rack,
                    vault_room, vault_branch, vault_location, is_active, status, created_by,
                    created_datetime, submitted_by, submitted_datetime, approved_by,
                    approved_datetime, updated_by, updated_datetime, version
                )
                SELECT vault_id, vault_tray_code, vault_tray_name, vault_shelf, vault_rack,
                       vault_room, vault_branch, vault_location, is_active, 'A', created_by,
                       created_datetime, submitted_by, submitted_datetime, ?, CURRENT_TIMESTAMP,
                       updated_by, updated_datetime, version
                FROM dms.vault_storage_wb
                WHERE wb_id = ?
                RETURNING vault_id
                """, Long.class, approver, request.wbId());
        if (vaultId == null) {
            throw new IllegalStateException("Approved vault record was not created");
        }
        return vaultId;
    }

    private void archiveApprovedRecord(VaultMaintenanceRecord record, String approver) {
        int archived = jdbcTemplate.update("""
                INSERT INTO dms.vault_storage_au (
                    vault_id, vault_tray_code, vault_tray_name, vault_shelf, vault_rack,
                    vault_room, vault_branch, vault_location, is_active, created_by,
                    created_datetime, submitted_by, submitted_datetime, approved_by,
                    approved_datetime, updated_by, updated_datetime, version,
                    archived_datetime, archived_by
                )
                SELECT vault_id, vault_tray_code, vault_tray_name, vault_shelf, vault_rack,
                       vault_room, vault_branch, vault_location, is_active, created_by,
                       created_datetime, submitted_by, submitted_datetime, approved_by,
                       approved_datetime, updated_by, updated_datetime, version,
                       CURRENT_TIMESTAMP, ?
                FROM dms.vault_storage_mst
                WHERE vault_tray_code = ? AND vault_id = ? AND status = 'A' AND version = ?
                """, approver, record.vaultTrayCode(), record.vaultId(), record.version());
        requireOneRow(archived, "Previously approved vault version was not archived");
    }

    private void updateMainRecord(
            VaultMaintenanceRecord request, VaultMaintenanceRecord current, String approver) {
        int updated = jdbcTemplate.update("""
                UPDATE dms.vault_storage_mst m
                SET vault_tray_name = w.vault_tray_name,
                    vault_shelf = w.vault_shelf,
                    vault_rack = w.vault_rack,
                    vault_room = w.vault_room,
                    vault_branch = w.vault_branch,
                    vault_location = w.vault_location,
                    is_active = w.is_active,
                    submitted_by = w.submitted_by,
                    submitted_datetime = w.submitted_datetime,
                    approved_by = ?, approved_datetime = CURRENT_TIMESTAMP,
                    updated_by = w.submitted_by,
                    updated_datetime = CURRENT_TIMESTAMP,
                    version = w.version
                FROM dms.vault_storage_wb w
                WHERE w.wb_id = ?
                  AND m.vault_tray_code = w.vault_tray_code
                  AND m.vault_tray_code = ?
                  AND m.vault_id = w.vault_id
                  AND m.vault_id = ?
                  AND m.status = 'A'
                  AND m.version = ?
                  AND w.version = m.version + 1
                """, approver,
                request.wbId(), request.vaultTrayCode(), current.vaultId(), current.version());
        requireOneRow(updated, "Vault record was not updated during approval");
    }

    private VaultMaintenanceAuditItem mapAuditItem(ResultSet rs, int row) throws SQLException {
        return new VaultMaintenanceAuditItem(
                rs.getLong("audit_id"),
                rs.getString("action_type"),
                rs.getString("wflow_type"),
                rs.getString("wflow_code"),
                rs.getString("action_by"),
                rs.getObject("action_at", OffsetDateTime.class),
                rs.getString("action_description"),
                rs.getString("remarks"));
    }

    private void insertAudit(
            String actionCode, String workflowCode, String username,
            String description, String remarks, String ipAddress) {
        jdbcTemplate.update("""
                INSERT INTO dms.app_audit (
                    action_type, wflow_type, wflow_code, action_by,
                    action_description, remarks, ip_address
                )
                VALUES (?, ?, ?, ?, ?, ?, ?::inet)
                """, actionCode, WORKFLOW_TYPE, workflowCode, username,
                description, remarks, ipAddress);
    }

    private String searchableRecords() {
        return """
                SELECT vault_id, NULL::uuid AS wb_id, vault_tray_code, vault_tray_name,
                       vault_shelf, vault_rack, vault_room, vault_branch, vault_location,
                       is_active, status, NULL::varchar AS action_type, created_by, created_datetime,
                       submitted_by, submitted_datetime, approved_by, approved_datetime,
                       NULL::varchar AS rejected_by, NULL::timestamptz AS rejected_datetime,
                       NULL::varchar AS rejection_reason, updated_by, updated_datetime, version
                FROM dms.vault_storage_mst
                UNION ALL
                SELECT vault_id, wb_id, vault_tray_code, vault_tray_name, vault_shelf,
                       vault_rack, vault_room, vault_branch, vault_location, is_active, status,
                       action_type, created_by, created_datetime, submitted_by, submitted_datetime,
                       approved_by, approved_datetime, rejected_by, rejected_datetime,
                       rejection_reason, updated_by, updated_datetime, version
                FROM dms.vault_storage_wb
                """;
    }

    private VaultMaintenanceRecord mapRecord(ResultSet rs, int row) throws SQLException {
        return new VaultMaintenanceRecord(
                rs.getObject("vault_id", Long.class),
                rs.getObject("wb_id", UUID.class),
                rs.getString("vault_tray_code"),
                rs.getString("vault_tray_name"),
                rs.getString("vault_shelf"),
                rs.getString("vault_rack"),
                rs.getString("vault_room"),
                rs.getString("vault_branch"),
                rs.getString("vault_location"),
                rs.getBoolean("is_active"),
                rs.getString("status"),
                rs.getString("action_type"),
                rs.getString("created_by"),
                rs.getObject("created_datetime", OffsetDateTime.class),
                rs.getString("submitted_by"),
                rs.getObject("submitted_datetime", OffsetDateTime.class),
                rs.getString("approved_by"),
                rs.getObject("approved_datetime", OffsetDateTime.class),
                rs.getString("rejected_by"),
                rs.getObject("rejected_datetime", OffsetDateTime.class),
                rs.getString("rejection_reason"),
                rs.getString("updated_by"),
                rs.getObject("updated_datetime", OffsetDateTime.class),
                rs.getObject("version", Integer.class));
    }

    private static VaultMaintenanceInput toInput(VaultMaintenanceRecord record) {
        return new VaultMaintenanceInput(
                record.vaultTrayCode(), record.vaultTrayName(), record.vaultShelf(),
                record.vaultRack(), record.vaultRoom(), record.vaultBranch(),
                record.vaultLocation(), record.isActive());
    }

    private static void requireApproved(VaultMaintenanceRecord record) {
        if (record == null || !"A".equals(record.status())) {
            throw new IllegalStateException("Only an approved vault record can be edited");
        }
    }

    private static void requirePending(VaultMaintenanceRecord record) {
        if (!"P".equals(record.status())) {
            throw new IllegalStateException("Vault workbench record is not pending approval");
        }
    }

    private static void requireEditableWorkbench(VaultMaintenanceRecord record) {
        if (!List.of("I", "R", "D").contains(record.status())) {
            throw new IllegalStateException("Vault workbench record is not editable");
        }
    }

    private static void requireDifferentReviewer(VaultMaintenanceRecord record, String username) {
        if (record.submittedBy() == null || record.submittedBy().equalsIgnoreCase(username)) {
            throw new IllegalStateException("The submitter cannot approve or reject their own vault request");
        }
    }

    private static void requireExpectedVersion(
            VaultMaintenanceRecord request, VaultMaintenanceRecord current) {
        if (request.version() == null || current.version() == null
                || request.version() != Math.addExact(current.version(), 1)) {
            throw new IllegalStateException(
                    "Vault record changed after this request was created; create a new edit request");
        }
    }

    private static int nextVersion(VaultMaintenanceRecord record) {
        if (record.version() == null || record.version() < 1) {
            throw new IllegalStateException("Approved vault record has an invalid version");
        }
        return Math.addExact(record.version(), 1);
    }

    private static void requireUnchangedTrayCode(
            VaultMaintenanceRecord current, VaultMaintenanceInput input) {
        if (!current.vaultTrayCode().equals(input.vaultTrayCode().trim())) {
            throw new IllegalArgumentException("vaultTrayCode cannot be changed for an existing record");
        }
    }

    private static void addLikeFilter(
            StringBuilder where, List<Object> parameters, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND LOWER(").append(column).append(") LIKE LOWER(?)");
            parameters.add("%" + value.trim() + "%");
        }
    }

    private static String clean(String value) {
        return value.trim();
    }

    private static String workbenchAuditCode(UUID wbId) {
        return "WB:" + wbId;
    }

    private static IllegalStateException duplicateTrayCode(DuplicateKeyException exception) {
        return new IllegalStateException(
                "A vault record or workbench request already exists for this tray code", exception);
    }

    private static void requireOneRow(int count, String message) {
        if (count != 1) {
            throw new IllegalStateException(message);
        }
    }

    private record WorkbenchIdentity(UUID wbId, long vaultId) {}
}
