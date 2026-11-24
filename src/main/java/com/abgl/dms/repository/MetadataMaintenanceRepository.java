package com.abgl.dms.repository;

import com.abgl.dms.metadata.MetadataMaintenanceAuditItem;
import com.abgl.dms.metadata.MetadataMaintenanceInput;
import com.abgl.dms.metadata.MetadataMaintenancePage;
import com.abgl.dms.metadata.MetadataMaintenanceRecord;
import com.abgl.dms.metadata.MetadataMaintenanceSearch;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class MetadataMaintenanceRepository {

    private static final String WORKFLOW_TYPE = "METADATA_DROPLIST";
    private static final String MAIN_COLUMNS = """
            id, NULL::uuid AS wb_id, md_type, md_code, md_desc, is_active,
            md_parent_type, status, created_by, created_datetime,
            NULL::varchar AS submitted_by, NULL::timestamptz AS submitted_datetime,
            approved_by, approved_datetime, NULL::varchar AS rejected_by,
            NULL::timestamptz AS rejected_datetime, NULL::varchar AS rejection_reason,
            updated_by, updated_datetime, NULL::varchar AS action_type, version
            """;
    private static final String WB_COLUMNS = """
            wb_id, md_type, md_code, md_desc, is_active, md_parent_type, status,
            created_by, created_datetime, submitted_by, submitted_datetime,
            approved_by, approved_datetime, rejected_by, rejected_datetime,
            rejection_reason, updated_by, updated_datetime, action_type, version
            """;

    private final JdbcTemplate jdbcTemplate;

    public MetadataMaintenanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findMetadataTypes() {
        return jdbcTemplate.queryForList("""
                SELECT md_type FROM dms.app_metadata_droplist
                UNION
                SELECT md_type FROM dms.app_metadata_droplist_wb
                UNION
                SELECT sd_type FROM dms.app_sysdata_droplist
                ORDER BY md_type
                """, String.class);
    }

    public MetadataMaintenancePage search(MetadataMaintenanceSearch search, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();
        addLikeFilter(where, parameters, "md_type", search.mdType());
        addLikeFilter(where, parameters, "md_code", search.mdCode());
        addLikeFilter(where, parameters, "md_desc", search.mdDesc());
        addLikeFilter(where, parameters, "md_parent_type", search.mdParentType());
        addLikeFilter(where, parameters, "status", search.status());
        addLikeFilter(where, parameters, "created_by", search.createdBy());
        if (search.isActive() != null) {
            where.append(" AND is_active = ?");
            parameters.add(search.isActive());
        }

        String records = searchableRecords();
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + records + ") metadata_records" + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<MetadataMaintenanceRecord> items = jdbcTemplate.query(
                "SELECT * FROM (" + records + ") metadata_records" + where
                        + " ORDER BY created_datetime DESC, COALESCE(id, 0) DESC, wb_id DESC NULLS LAST LIMIT ? OFFSET ?",
                (rs, row) -> mapRecord(rs, rs.getObject("wb_id") != null), pageParameters.toArray());
        return new MetadataMaintenancePage(items, total == null ? 0 : total, page, size);
    }

    public MetadataMaintenanceRecord findRecord(long id) {
        List<MetadataMaintenanceRecord> records = jdbcTemplate.query(
                "SELECT " + MAIN_COLUMNS + " FROM dms.app_metadata_droplist WHERE id = ?",
                this::mapMainRecord, id);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Metadata record not found");
        }
        return records.getFirst();
    }

    public MetadataMaintenanceRecord findWorkbench(UUID wbId) {
        return findWorkbench(wbId, false);
    }

    public List<MetadataMaintenanceRecord> findPending() {
        return jdbcTemplate.query("""
                SELECT wb_id, md_type, md_code, md_desc, is_active, md_parent_type, status,
                       created_by, created_datetime, submitted_by, submitted_datetime,
                       approved_by, approved_datetime, rejected_by, rejected_datetime,
                       rejection_reason, updated_by, updated_datetime, action_type, version
                FROM dms.app_metadata_droplist_wb
                WHERE status = 'P'
                ORDER BY submitted_datetime, wb_id
                """, this::mapWorkbenchRecord);
    }

    @Transactional
    public MetadataMaintenanceRecord create(MetadataMaintenanceInput input, String username, String ipAddress) {
        return insertWorkbench(input, "I", "P", 1, username, username, ipAddress);
    }

    @Transactional
    public MetadataMaintenanceRecord createDraft(
            MetadataMaintenanceInput input, String username, String ipAddress) {
        return insertWorkbench(input, "I", "I", 1, username, null, ipAddress);
    }

    @Transactional
    public MetadataMaintenanceRecord createEditDraft(
            long id, MetadataMaintenanceInput input, String username, String ipAddress) {
        MetadataMaintenanceRecord approved = findMainForUpdate(id);
        requireApprovedEditable(approved);
        requireUnchangedKey(approved, input);
        ensureNoSystemDataCollision(input.mdType(), input.mdCode());
        return insertWorkbench(input, "U", "I", nextVersion(approved), username, null, ipAddress);
    }

    @Transactional
    public MetadataMaintenanceRecord updateDraft(
            UUID wbId, MetadataMaintenanceInput input, String username, String ipAddress) {
        MetadataMaintenanceRecord before = findWorkbenchForUpdate(wbId);
        requireDraft(before);
        ensureNoSystemDataCollision(input.mdType(), input.mdCode());
        if ("I".equals(before.actionType())) {
            ensureNoMainRecord(input.mdType(), input.mdCode());
        } else {
            requireUnchangedKey(before, input);
        }
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE dms.app_metadata_droplist_wb
                    SET md_type = ?, md_code = ?, md_desc = ?, md_parent_type = ?,
                        is_active = ?, status = 'I',
                        submitted_by = NULL, submitted_datetime = NULL,
                        rejected_by = NULL, rejected_datetime = NULL, rejection_reason = NULL,
                        updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                    WHERE wb_id = ? AND status IN ('I', 'R', 'D')
                    """, clean(input.mdType()), clean(input.mdCode()), clean(input.mdDesc()),
                    cleanNullable(input.mdParentType()), activeValue(input, before.isActive()),
                    username, wbId);
            requireOneRow(updated, "Metadata draft was not updated");
        } catch (DuplicateKeyException exception) {
            throw duplicateMetadata(exception);
        }
        insertAudit("U", workbenchAuditCode(wbId), username,
                "Updated metadata draft", "Metadata draft updated.", ipAddress);
        return findWorkbench(wbId);
    }

    @Transactional
    public MetadataMaintenanceRecord submit(UUID wbId, String username, String ipAddress) {
        MetadataMaintenanceRecord draft = findWorkbenchForUpdate(wbId);
        requireDraft(draft);
        validateSubmission(draft);
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_metadata_droplist_wb
                SET status = 'P', submitted_by = ?, submitted_datetime = CURRENT_TIMESTAMP,
                    rejected_by = NULL, rejected_datetime = NULL, rejection_reason = NULL,
                    updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                WHERE wb_id = ? AND status IN ('I', 'R', 'D')
                """, username, username, wbId);
        requireOneRow(updated, "Metadata draft was not submitted");
        insertAudit("P", workbenchAuditCode(wbId), username,
                "Submitted metadata record", "Metadata submitted for approval.", ipAddress);
        return findWorkbench(wbId);
    }

    @Transactional
    public MetadataMaintenanceRecord update(
            long id, MetadataMaintenanceInput input, String username, String ipAddress) {
        MetadataMaintenanceRecord approved = findMainForUpdate(id);
        requireApprovedEditable(approved);
        requireUnchangedKey(approved, input);
        ensureNoSystemDataCollision(input.mdType(), input.mdCode());
        MetadataMaintenanceRecord existingRequest = findWorkbenchByKey(
                approved.mdType(), approved.mdCode(), true);
        if (existingRequest != null && "P".equals(existingRequest.status())) {
            throw new IllegalStateException("A metadata request is already pending for this type and code");
        }
        if (existingRequest == null) {
            return insertWorkbench(input, "U", "P", nextVersion(approved), username, username, ipAddress);
        }
        resetWorkbenchRequest(existingRequest.wbId(), input, "U", "P", nextVersion(approved), username);
        insertAudit("U", workbenchAuditCode(existingRequest.wbId()), username,
                "Submitted metadata changes", "Metadata changes submitted for approval.", ipAddress);
        insertAudit("P", workbenchAuditCode(existingRequest.wbId()), username,
                "Submitted metadata record", "Metadata submitted for approval.", ipAddress);
        return findWorkbench(existingRequest.wbId());
    }

    @Transactional
    public MetadataMaintenanceRecord approve(UUID wbId, String username, String ipAddress) {
        MetadataMaintenanceRecord request = findWorkbenchForUpdate(wbId);
        requirePending(request);
        requireDifferentReviewer(request, username);

        Long mainId;
        if ("I".equals(request.actionType())) {
            ensureNoMainRecord(request.mdType(), request.mdCode());
            mainId = insertApprovedRecord(request, username);
        } else {
            MetadataMaintenanceRecord current = findMainByKeyForUpdate(request.mdType(), request.mdCode());
            if (current == null || !"A".equals(current.status())) {
                throw new IllegalStateException("The approved metadata record no longer exists");
            }
            requireExpectedVersion(request, current);
            archiveApprovedRecord(current, request.actionType());
            if ("D".equals(request.actionType())) {
                markMainRecordDeleted(current.id(), username);
            } else if ("U".equals(request.actionType())) {
                updateMainRecord(request, username);
            } else {
                throw new IllegalStateException("Unsupported metadata action type");
            }
            mainId = current.id();
        }

        jdbcTemplate.update("""
                UPDATE dms.app_audit
                SET wflow_code = ?
                WHERE wflow_type = ? AND wflow_code = ?
                """, Long.toString(mainId), WORKFLOW_TYPE, workbenchAuditCode(wbId));
        int removed = jdbcTemplate.update(
                "DELETE FROM dms.app_metadata_droplist_wb WHERE wb_id = ? AND status = 'P'", wbId);
        requireOneRow(removed, "Approved metadata request was not removed from workbench");
        insertAudit("A", Long.toString(mainId), username,
                "Approved metadata record", "Metadata record approved.", ipAddress);
        return findRecord(mainId);
    }

    @Transactional
    public MetadataMaintenanceRecord reject(
            UUID wbId, String username, String reason, String ipAddress) {
        MetadataMaintenanceRecord request = findWorkbenchForUpdate(wbId);
        requirePending(request);
        requireDifferentReviewer(request, username);
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_metadata_droplist_wb
                SET status = 'R', rejected_by = ?, rejected_datetime = CURRENT_TIMESTAMP,
                    rejection_reason = ?, updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                WHERE wb_id = ? AND status = 'P'
                """, username, clean(reason), username, wbId);
        requireOneRow(updated, "Metadata request was not rejected");
        insertAudit("R", workbenchAuditCode(wbId), username,
                "Rejected metadata record", clean(reason), ipAddress);
        return findWorkbench(wbId);
    }

    @Transactional
    public MetadataMaintenanceRecord delete(long id, String username, String ipAddress) {
        MetadataMaintenanceRecord current = findMainForUpdate(id);
        requireApprovedEditable(current);
        MetadataMaintenanceRecord existingRequest = findWorkbenchByKey(current.mdType(), current.mdCode(), true);
        if (existingRequest != null && "P".equals(existingRequest.status())) {
            throw new IllegalStateException("A metadata request is already pending for this type and code");
        }
        MetadataMaintenanceInput input = new MetadataMaintenanceInput(
                current.mdType(), current.mdCode(), current.mdDesc(), current.mdParentType(), false);
        MetadataMaintenanceRecord request;
        if (existingRequest == null) {
            request = insertWorkbench(input, "D", "P", recordVersion(current), username, username, ipAddress);
        } else {
            resetWorkbenchRequest(existingRequest.wbId(), input, "D", "P", recordVersion(current), username);
            request = findWorkbench(existingRequest.wbId());
        }
        insertAudit("D", workbenchAuditCode(request.wbId()), username,
                "Submitted metadata deletion", "Metadata deletion submitted for approval.", ipAddress);
        insertAudit("P", workbenchAuditCode(request.wbId()), username,
                "Submitted metadata record", "Metadata deletion submitted for approval.", ipAddress);
        return request;
    }

    @Transactional
    public MetadataMaintenanceRecord deleteDraft(UUID wbId, String username, String ipAddress) {
        MetadataMaintenanceRecord draft = findWorkbenchForUpdate(wbId);
        if (!"I".equals(draft.status()) && !"R".equals(draft.status())) {
            throw new IllegalStateException("Only a draft or rejected request can be deleted");
        }
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_metadata_droplist_wb
                SET status = 'D', updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                WHERE wb_id = ? AND status IN ('I', 'R')
                """, username, wbId);
        requireOneRow(updated, "Metadata draft was not deleted");
        insertAudit("D", workbenchAuditCode(wbId), username,
                "Deleted metadata draft", "Metadata workbench record deleted.", ipAddress);
        return findWorkbench(wbId);
    }

    public List<MetadataMaintenanceAuditItem> findAudit(long id) {
        findRecord(id);
        return findAuditByWorkflowCode(Long.toString(id));
    }

    public List<MetadataMaintenanceAuditItem> findWorkbenchAudit(UUID wbId) {
        findWorkbench(wbId);
        return findAuditByWorkflowCode(workbenchAuditCode(wbId));
    }

    private List<MetadataMaintenanceAuditItem> findAuditByWorkflowCode(String code) {
        return jdbcTemplate.query("""
                SELECT audit_id, action_type, wflow_type, wflow_code, action_by,
                       action_at, action_description, remarks
                FROM dms.app_audit
                WHERE wflow_type = ? AND wflow_code = ?
                ORDER BY action_at DESC, audit_id DESC
                """, (rs, row) -> new MetadataMaintenanceAuditItem(
                        rs.getLong("audit_id"),
                        rs.getString("action_type"),
                        rs.getString("wflow_type"),
                        rs.getString("wflow_code"),
                        rs.getString("action_by"),
                        rs.getObject("action_at", OffsetDateTime.class),
                        rs.getString("action_description"),
                        rs.getString("remarks")),
                WORKFLOW_TYPE, code);
    }

    private MetadataMaintenanceRecord insertWorkbench(
            MetadataMaintenanceInput input, String action, String status, int version,
            String username, String submitter, String ipAddress) {
        ensureNoSystemDataCollision(input.mdType(), input.mdCode());
        if ("I".equals(action)) {
            ensureNoMainRecord(input.mdType(), input.mdCode());
        }
        MetadataMaintenanceRecord existing = findWorkbenchByKey(input.mdType(), input.mdCode(), true);
        if (existing != null) {
            if ("R".equals(existing.status()) || "D".equals(existing.status())) {
                resetWorkbenchRequest(existing.wbId(), input, action, status, version, username);
                insertAudit(action, workbenchAuditCode(existing.wbId()), username,
                        "Saved metadata workbench record",
                        "Metadata record added to workbench.", ipAddress);
                if ("P".equals(status)) {
                    insertAudit("P", workbenchAuditCode(existing.wbId()), username,
                            "Submitted metadata record", "Metadata submitted for approval.", ipAddress);
                }
                return findWorkbench(existing.wbId());
            }
            throw new IllegalStateException("A metadata workbench record already exists for this type and code");
        }
        try {
            UUID wbId = jdbcTemplate.queryForObject("""
                    INSERT INTO dms.app_metadata_droplist_wb (
                        md_type, md_code, md_desc, md_parent_type, is_active,
                        action_type, status, created_by, submitted_by, submitted_datetime,
                        updated_by, updated_datetime, version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,
                            CASE WHEN CAST(? AS varchar) IS NULL THEN NULL ELSE CURRENT_TIMESTAMP END,
                            ?, CASE WHEN CAST(? AS varchar) IS NULL THEN NULL ELSE CURRENT_TIMESTAMP END, ?)
                    RETURNING wb_id
                    """, UUID.class,
                    clean(input.mdType()), clean(input.mdCode()), clean(input.mdDesc()),
                    cleanNullable(input.mdParentType()), activeValue(input), action, status,
                    username, submitter, submitter, submitter, submitter, version);
            if (wbId == null) {
                throw new IllegalStateException("Metadata workbench record was not created");
            }
            insertAudit(action, workbenchAuditCode(wbId), username,
                    "Saved metadata workbench record",
                    "Metadata record added to workbench.", ipAddress);
            if ("P".equals(status)) {
                insertAudit("P", workbenchAuditCode(wbId), username,
                        "Submitted metadata record", "Metadata submitted for approval.", ipAddress);
            }
            return findWorkbench(wbId);
        } catch (DuplicateKeyException exception) {
            throw duplicateMetadata(exception);
        }
    }

    private void resetWorkbenchRequest(
            UUID wbId, MetadataMaintenanceInput input, String action, String status,
            int version, String username) {
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE dms.app_metadata_droplist_wb
                    SET md_desc = ?, md_parent_type = ?, is_active = ?, action_type = ?,
                        status = ?, created_by = ?, created_datetime = CURRENT_TIMESTAMP,
                        submitted_by = CASE WHEN ? = 'P' THEN ? ELSE NULL END,
                        submitted_datetime = CASE WHEN ? = 'P' THEN CURRENT_TIMESTAMP ELSE NULL END,
                        rejected_by = NULL, rejected_datetime = NULL, rejection_reason = NULL,
                        updated_by = ?, updated_datetime = CURRENT_TIMESTAMP,
                        version = ?
                    WHERE wb_id = ? AND status IN ('I', 'R', 'D')
                    """, clean(input.mdDesc()), cleanNullable(input.mdParentType()),
                    activeValue(input), action, status, username, status, username, status,
                    username, version, wbId);
            requireOneRow(updated, "Metadata workbench request cannot be reused");
        } catch (DuplicateKeyException exception) {
            throw duplicateMetadata(exception);
        }
    }

    private void validateSubmission(MetadataMaintenanceRecord request) {
        ensureNoSystemDataCollision(request.mdType(), request.mdCode());
        if ("I".equals(request.actionType())) {
            ensureNoMainRecord(request.mdType(), request.mdCode());
        } else if ("U".equals(request.actionType()) || "D".equals(request.actionType())) {
            MetadataMaintenanceRecord current = findMainByKey(request.mdType(), request.mdCode());
            if (current == null || !"A".equals(current.status())) {
                throw new IllegalStateException("The approved metadata record no longer exists");
            }
        } else {
            throw new IllegalStateException("Unsupported metadata action type");
        }
    }

    private void ensureNoSystemDataCollision(String mdType, String mdCode) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM dms.app_sysdata_droplist
                    WHERE LOWER(sd_type) = LOWER(?) AND LOWER(sd_code) = LOWER(?)
                )
                """, Boolean.class, mdType, mdCode);
        if (Boolean.TRUE.equals(exists)) {
            throw new IllegalStateException("Metadata type and code conflict with a system drop-list value");
        }
    }

    private void ensureNoMainRecord(String mdType, String mdCode) {
        if (findMainByKey(mdType, mdCode) != null) {
            throw new IllegalStateException("A metadata record already exists for this type and code");
        }
    }

    private MetadataMaintenanceRecord findWorkbench(UUID wbId, boolean forUpdate) {
        String sql = "SELECT " + WB_COLUMNS
                + " FROM dms.app_metadata_droplist_wb WHERE wb_id = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        List<MetadataMaintenanceRecord> records = jdbcTemplate.query(
                sql, this::mapWorkbenchRecord, wbId);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Metadata workbench record not found");
        }
        return records.getFirst();
    }

    private MetadataMaintenanceRecord findWorkbenchForUpdate(UUID wbId) {
        return findWorkbench(wbId, true);
    }

    private MetadataMaintenanceRecord findWorkbenchByKey(String mdType, String mdCode, boolean forUpdate) {
        String sql = "SELECT " + WB_COLUMNS
                + " FROM dms.app_metadata_droplist_wb"
                + " WHERE md_type = ? AND md_code = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        List<MetadataMaintenanceRecord> records = jdbcTemplate.query(
                sql, this::mapWorkbenchRecord, mdType, mdCode);
        return records.isEmpty() ? null : records.getFirst();
    }

    private MetadataMaintenanceRecord findMainByKey(String mdType, String mdCode) {
        String sql = "SELECT " + MAIN_COLUMNS
                + " FROM dms.app_metadata_droplist WHERE md_type = ? AND md_code = ?";
        List<MetadataMaintenanceRecord> records = jdbcTemplate.query(
                sql, this::mapMainRecord, mdType, mdCode);
        return records.isEmpty() ? null : records.getFirst();
    }

    private MetadataMaintenanceRecord findMainByKeyForUpdate(String mdType, String mdCode) {
        String sql = "SELECT " + MAIN_COLUMNS
                + " FROM dms.app_metadata_droplist WHERE md_type = ? AND md_code = ? FOR UPDATE";
        List<MetadataMaintenanceRecord> records = jdbcTemplate.query(
                sql, this::mapMainRecord, mdType, mdCode);
        return records.isEmpty() ? null : records.getFirst();
    }

    private MetadataMaintenanceRecord findMainForUpdate(long id) {
        List<MetadataMaintenanceRecord> records = jdbcTemplate.query(
                "SELECT " + MAIN_COLUMNS + " FROM dms.app_metadata_droplist WHERE id = ? FOR UPDATE",
                this::mapMainRecord, id);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Metadata record not found");
        }
        return records.getFirst();
    }

    private Long insertApprovedRecord(MetadataMaintenanceRecord request, String approver) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO dms.app_metadata_droplist (
                    md_type, md_code, md_desc, md_parent_type, status, is_active,
                    created_by, created_datetime, approved_by, approved_datetime,
                    updated_by, updated_datetime, version
                )
                SELECT md_type, md_code, md_desc, md_parent_type, 'A', is_active,
                       created_by, created_datetime, ?, CURRENT_TIMESTAMP,
                       updated_by, updated_datetime, version
                FROM dms.app_metadata_droplist_wb
                WHERE wb_id = ?
                RETURNING id
                """, Long.class, approver, request.wbId());
        if (id == null) {
            throw new IllegalStateException("Approved metadata record was not created");
        }
        return id;
    }

    private void archiveApprovedRecord(MetadataMaintenanceRecord record, String actionType) {
        int archived = jdbcTemplate.update("""
                INSERT INTO dms.app_metadata_droplist_au (
                    id, md_type, md_code, md_desc, status, is_active, md_parent_type,
                    action_type, created_by, created_datetime, approved_by, approved_datetime,
                    updated_by, updated_datetime, version, audit_date
                )
                SELECT id, md_type, md_code, md_desc, 'A', is_active, md_parent_type,
                       ?, created_by, created_datetime, approved_by, approved_datetime,
                       updated_by, updated_datetime, version, CURRENT_TIMESTAMP
                FROM dms.app_metadata_droplist
                WHERE md_type = ? AND md_code = ? AND status = 'A'
                """, actionType, record.mdType(), record.mdCode());
        requireOneRow(archived, "Previously approved metadata version was not archived");
    }

    private void updateMainRecord(MetadataMaintenanceRecord request, String approver) {
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_metadata_droplist
                SET md_desc = ?, md_parent_type = ?, is_active = ?, status = 'A',
                    approved_by = ?, approved_datetime = CURRENT_TIMESTAMP,
                    updated_by = ?, updated_datetime = CURRENT_TIMESTAMP, version = ?
                WHERE md_type = ? AND md_code = ? AND status = 'A'
                """, request.mdDesc(), request.mdParentType(), request.isActive(), approver,
                request.submittedBy(), request.version(), request.mdType(), request.mdCode());
        requireOneRow(updated, "Metadata record was not updated during approval");
    }

    private void markMainRecordDeleted(long id, String approver) {
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_metadata_droplist
                SET status = 'D', is_active = FALSE, approved_by = ?,
                    approved_datetime = CURRENT_TIMESTAMP, updated_by = ?,
                    updated_datetime = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'A'
                """, approver, approver, id);
        requireOneRow(updated, "Metadata record was not deleted during approval");
    }

    private void insertAudit(
            String actionCode, String workflowCode, String username,
            String description, String remarks, String ipAddress) {
        jdbcTemplate.update("""
                INSERT INTO dms.app_audit (
                    action_type, wflow_type, wflow_code, action_by,
                    action_description, remarks, ip_address
                ) VALUES (?, ?, ?, ?, ?, ?, ?::inet)
                """, actionCode, WORKFLOW_TYPE, workflowCode, username,
                description, remarks, ipAddress);
    }

    private String searchableRecords() {
        return """
                SELECT id, NULL::uuid AS wb_id, md_type, md_code, md_desc, is_active,
                       md_parent_type, status, created_by, created_datetime,
                       NULL::varchar AS submitted_by, NULL::timestamptz AS submitted_datetime,
                       approved_by, approved_datetime, NULL::varchar AS rejected_by,
                       NULL::timestamptz AS rejected_datetime, NULL::varchar AS rejection_reason,
                       updated_by, updated_datetime, NULL::varchar AS action_type,
                       version
                FROM dms.app_metadata_droplist
                UNION ALL
                SELECT NULL::bigint AS id, wb_id, md_type, md_code, md_desc, is_active,
                       md_parent_type, status, created_by, created_datetime,
                       submitted_by, submitted_datetime, approved_by, approved_datetime,
                       rejected_by, rejected_datetime, rejection_reason, updated_by,
                       updated_datetime, action_type, version
                FROM dms.app_metadata_droplist_wb
                """;
    }

    private MetadataMaintenanceRecord mapMainRecord(ResultSet rs, int row) throws SQLException {
        return mapRecord(rs, false);
    }

    private MetadataMaintenanceRecord mapWorkbenchRecord(ResultSet rs, int row) throws SQLException {
        return mapRecord(rs, true);
    }

    private MetadataMaintenanceRecord mapRecord(ResultSet rs, boolean workbench) throws SQLException {
        return new MetadataMaintenanceRecord(
                workbench ? null : rs.getLong("id"),
                workbench ? rs.getObject("wb_id", UUID.class) : null,
                rs.getString("md_type"),
                rs.getString("md_code"),
                rs.getString("md_desc"),
                rs.getBoolean("is_active"),
                rs.getString("md_parent_type"),
                rs.getString("status"),
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
                rs.getString("action_type"),
                (Integer) rs.getObject("version")
        );
    }

    private static void requireDraft(MetadataMaintenanceRecord record) {
        if (!"I".equals(record.status())
                && !"R".equals(record.status())
                && !"D".equals(record.status())) {
            throw new IllegalStateException("Metadata workbench record is not a draft");
        }
    }

    private static void requirePending(MetadataMaintenanceRecord record) {
        if (!"P".equals(record.status())) {
            throw new IllegalStateException("Metadata workbench record is not pending approval");
        }
    }

    private static void requireDifferentReviewer(MetadataMaintenanceRecord record, String username) {
        if (record.submittedBy() == null || record.submittedBy().equalsIgnoreCase(username)) {
            throw new IllegalStateException("The submitter cannot approve or reject their own metadata");
        }
    }

    private static void requireApprovedEditable(MetadataMaintenanceRecord record) {
        if (!"A".equals(record.status())) {
            throw new IllegalStateException("Only approved metadata can be edited or deleted");
        }
    }

    private static int nextVersion(MetadataMaintenanceRecord record) {
        return Math.addExact(recordVersion(record), 1);
    }

    private static int recordVersion(MetadataMaintenanceRecord record) {
        if (record.version() == null || record.version() < 1) {
            throw new IllegalStateException(
                    "Approved metadata version is missing; apply the metadata versioning migration");
        }
        return record.version();
    }

    private static void requireExpectedVersion(
            MetadataMaintenanceRecord request, MetadataMaintenanceRecord current) {
        int expectedVersion = "U".equals(request.actionType())
                ? nextVersion(current)
                : recordVersion(current);
        if (request.version() == null || request.version() != expectedVersion) {
            throw new IllegalStateException(
                    "Metadata record changed after this request was created; create a new edit request");
        }
    }

    private static void requireUnchangedKey(
            MetadataMaintenanceRecord current, MetadataMaintenanceInput input) {
        if (!current.mdType().equals(input.mdType().trim())
                || !current.mdCode().equals(input.mdCode().trim())) {
            throw new IllegalStateException("mdType and mdCode cannot be changed for an existing record");
        }
    }

    private static void addLikeFilter(
            StringBuilder where, List<Object> parameters, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND LOWER(").append(column).append(") LIKE LOWER(?)");
            parameters.add("%" + value.trim() + "%");
        }
    }

    private static boolean activeValue(MetadataMaintenanceInput input) {
        return input.isActive() == null || input.isActive();
    }

    private static boolean activeValue(MetadataMaintenanceInput input, boolean currentValue) {
        return input.isActive() == null ? currentValue : input.isActive();
    }

    private static String clean(String value) {
        return value.trim();
    }

    private static String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String workbenchAuditCode(UUID wbId) {
        return "WB:" + wbId;
    }

    private static IllegalStateException duplicateMetadata(DuplicateKeyException exception) {
        return new IllegalStateException("A metadata record already exists for this type and code", exception);
    }

    private static void requireOneRow(int count, String message) {
        if (count != 1) {
            throw new IllegalStateException(message);
        }
    }
}
