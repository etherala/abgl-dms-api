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

@Repository
public class MetadataMaintenanceRepository {

    private static final String FIND_RECORD = """
            SELECT id, md_type, md_code, md_desc, is_active, md_parent_type, status,
                   created_by, created_datetime, approved_by, approved_datetime,
                   updated_by, updated_datetime
            FROM dms.app_metadata_droplist
            WHERE id = ?
            """;

    private static final String AUDIT_WORKFLOW_TYPE = "METADATA_DROPLIST";

    private final JdbcTemplate jdbcTemplate;

    public MetadataMaintenanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findMetadataTypes() {
        return jdbcTemplate.queryForList("""
                SELECT md_type
                FROM dms.app_metadata_droplist
                UNION
                SELECT sd_type
                FROM dms.app_sysdata_droplist
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

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dms.app_metadata_droplist" + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);

        List<MetadataMaintenanceRecord> items = jdbcTemplate.query("""
                SELECT id, md_type, md_code, md_desc, is_active, md_parent_type, status,
                       created_by, created_datetime, approved_by, approved_datetime,
                       updated_by, updated_datetime
                FROM dms.app_metadata_droplist
                """ + where + """
                ORDER BY created_datetime DESC, id DESC
                LIMIT ? OFFSET ?
                """, this::mapRecord, pageParameters.toArray());
        return new MetadataMaintenancePage(items, total == null ? 0 : total, page, size);
    }

    public MetadataMaintenanceRecord findRecord(long id) {
        List<MetadataMaintenanceRecord> records = jdbcTemplate.query(FIND_RECORD, this::mapRecord, id);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Metadata record not found");
        }
        return records.get(0);
    }

    public List<MetadataMaintenanceRecord> findPending() {
        return jdbcTemplate.query("""
                SELECT id, md_type, md_code, md_desc, is_active, md_parent_type, status,
                       created_by, created_datetime, approved_by, approved_datetime,
                       updated_by, updated_datetime
                FROM dms.app_metadata_droplist
                WHERE status = 'P'
                ORDER BY created_datetime, id
                """, this::mapRecord);
    }

    @Transactional
    public MetadataMaintenanceRecord create(
            MetadataMaintenanceInput input, String username, String ipAddress) {
        ensureNoSystemDataCollision(input.mdType(), input.mdCode());
        try {
            Long id = jdbcTemplate.queryForObject("""
                    INSERT INTO dms.app_metadata_droplist
                        (md_type, md_code, md_desc, md_parent_type, status, is_active, created_by)
                    VALUES (?, ?, ?, ?, 'P', ?, ?)
                    RETURNING id
                    """, Long.class,
                    clean(input.mdType()), clean(input.mdCode()), clean(input.mdDesc()),
                    cleanNullable(input.mdParentType()), activeValue(input), username);
            if (id == null) {
                throw new IllegalStateException("Metadata record was not created");
            }
            MetadataMaintenanceRecord record = findRecord(id);
            insertAudit("I", id, username, "Created metadata record",
                    "Metadata record submitted for approval.", ipAddress);
            return record;
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("A metadata record already exists for this type and code", exception);
        }
    }

    @Transactional
    public MetadataMaintenanceRecord createDraft(
            MetadataMaintenanceInput input, String username, String ipAddress) {
        ensureNoSystemDataCollision(input.mdType(), input.mdCode());
        try {
            Long id = jdbcTemplate.queryForObject("""
                    INSERT INTO dms.app_metadata_droplist
                        (md_type, md_code, md_desc, md_parent_type, status, is_active, created_by)
                    VALUES (?, ?, ?, ?, 'I', ?, ?)
                    RETURNING id
                    """, Long.class,
                    clean(input.mdType()), clean(input.mdCode()), clean(input.mdDesc()),
                    cleanNullable(input.mdParentType()), activeValue(input), username);
            if (id == null) {
                throw new IllegalStateException("Metadata draft was not saved");
            }
            MetadataMaintenanceRecord record = findRecord(id);
            insertAudit("I", id, username, "Saved metadata draft",
                    "Metadata draft saved.", ipAddress);
            return record;
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("A metadata record already exists for this type and code", exception);
        }
    }

    @Transactional
    public MetadataMaintenanceRecord updateDraft(
            long id, MetadataMaintenanceInput input, String username, String ipAddress) {
        MetadataMaintenanceRecord before = findRecordForUpdate(id);
        requireDraft(before);
        ensureNoSystemDataCollision(input.mdType(), input.mdCode());

        try {
            int updated = jdbcTemplate.update("""
                    UPDATE dms.app_metadata_droplist
                    SET md_type = ?, md_code = ?, md_desc = ?, md_parent_type = ?,
                        is_active = ?, updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'I'
                    """, clean(input.mdType()), clean(input.mdCode()), clean(input.mdDesc()),
                    cleanNullable(input.mdParentType()), activeValue(input, before.isActive()),
                    username, id);
            requireOneRow(updated, "Metadata draft was not updated");
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("A metadata record already exists for this type and code", exception);
        }

        MetadataMaintenanceRecord updatedRecord = findRecord(id);
        insertAudit("U", id, username, "Updated metadata draft",
                "Metadata draft updated.", ipAddress);
        return updatedRecord;
    }

    @Transactional
    public MetadataMaintenanceRecord submit(long id, String username, String ipAddress) {
        MetadataMaintenanceRecord before = findRecordForUpdate(id);
        requireDraft(before);
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_metadata_droplist
                SET status = 'P', updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'I'
                """, username, id);
        requireOneRow(updated, "Metadata draft was not submitted");
        MetadataMaintenanceRecord after = findRecord(id);
        insertAudit("P", id, username, "Submitted metadata record",
                "Metadata submitted for approval.", ipAddress);
        return after;
    }

    @Transactional
    public MetadataMaintenanceRecord update(
            long id, MetadataMaintenanceInput input, String username, String ipAddress) {
        MetadataMaintenanceRecord before = findRecordForUpdate(id);
        requireEditable(before);
        ensureNoSystemDataCollision(input.mdType(), input.mdCode());

        try {
            int updated = jdbcTemplate.update("""
                    UPDATE dms.app_metadata_droplist
                    SET md_type = ?, md_code = ?, md_desc = ?, md_parent_type = ?,
                        status = 'P', is_active = ?,
                        updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, clean(input.mdType()), clean(input.mdCode()), clean(input.mdDesc()),
                    cleanNullable(input.mdParentType()), activeValue(input, before.isActive()),
                    username, id);
            requireOneRow(updated, "Metadata record was not updated");
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("A metadata record already exists for this type and code", exception);
        }

        MetadataMaintenanceRecord after = findRecord(id);
        insertAudit("U", id, username, "Updated metadata record",
                "Metadata changes submitted for approval.", ipAddress);
        return after;
    }

    @Transactional
    public MetadataMaintenanceRecord approve(long id, String username, String ipAddress) {
        MetadataMaintenanceRecord before = findRecordForUpdate(id);
        requirePending(before);
        requireDifferentReviewer(before, username);
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_metadata_droplist
                SET status = 'A', approved_by = ?, approved_datetime = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'P'
                """, username, id);
        requireOneRow(updated, "Metadata record was not approved");
        MetadataMaintenanceRecord after = findRecord(id);
        insertAudit("A", id, username, "Approved metadata record",
                "Metadata record approved.", ipAddress);
        return after;
    }

    @Transactional
    public MetadataMaintenanceRecord reject(
            long id, String username, String reason, String ipAddress) {
        MetadataMaintenanceRecord before = findRecordForUpdate(id);
        requirePending(before);
        requireDifferentReviewer(before, username);
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_metadata_droplist
                SET status = 'R', is_active = false,
                    updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'P'
                """, username, id);
        requireOneRow(updated, "Metadata record was not rejected");
        MetadataMaintenanceRecord after = findRecord(id);
        insertAudit("R", id, username, "Rejected metadata record", clean(reason), ipAddress);
        return after;
    }

    @Transactional
    public MetadataMaintenanceRecord delete(long id, String username, String ipAddress) {
        MetadataMaintenanceRecord before = findRecordForUpdate(id);
        if ("D".equals(before.status())) {
            throw new IllegalStateException("Metadata record is already deleted");
        }
        if ("P".equals(before.status())) {
            throw new IllegalStateException("Pending metadata cannot be deleted");
        }
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_metadata_droplist
                SET status = 'D', is_active = false,
                    updated_by = ?, updated_datetime = CURRENT_TIMESTAMP
                WHERE id = ?
                """, username, id);
        requireOneRow(updated, "Metadata record was not deleted");
        MetadataMaintenanceRecord after = findRecord(id);
        insertAudit("D", id, username, "Deleted metadata record",
                "Metadata record deleted.", ipAddress);
        return after;
    }

    public List<MetadataMaintenanceAuditItem> findAudit(long id) {
        return jdbcTemplate.query("""
                SELECT audit_id, action_type, wflow_type, wflow_code, action_by,
                       action_at, action_description, remarks
                FROM dms.app_audit
                WHERE wflow_type = ? AND wflow_code = ?
                ORDER BY action_at DESC, audit_id DESC
                """, (rs, row) -> mapAudit(rs),
                AUDIT_WORKFLOW_TYPE, Long.toString(id));
    }

    private MetadataMaintenanceRecord findRecordForUpdate(long id) {
        List<MetadataMaintenanceRecord> records = jdbcTemplate.query(
                FIND_RECORD + " FOR UPDATE", this::mapRecord, id);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Metadata record not found");
        }
        return records.get(0);
    }

    private MetadataMaintenanceRecord mapRecord(ResultSet rs, int row) throws SQLException {
        return new MetadataMaintenanceRecord(
                rs.getLong("id"),
                rs.getString("md_type"),
                rs.getString("md_code"),
                rs.getString("md_desc"),
                rs.getBoolean("is_active"),
                rs.getString("md_parent_type"),
                rs.getString("status"),
                rs.getString("created_by"),
                rs.getObject("created_datetime", OffsetDateTime.class),
                rs.getString("approved_by"),
                rs.getObject("approved_datetime", OffsetDateTime.class),
                rs.getString("updated_by"),
                rs.getObject("updated_datetime", OffsetDateTime.class)
        );
    }

    private void ensureNoSystemDataCollision(String metadataType, String metadataCode) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM dms.app_sysdata_droplist
                    WHERE LOWER(sd_type) = LOWER(?) AND LOWER(sd_code) = LOWER(?)
                )
                """, Boolean.class, metadataType, metadataCode);
        if (Boolean.TRUE.equals(exists)) {
            throw new IllegalStateException("Metadata type and code conflict with a system drop-list value");
        }
    }

    private void insertAudit(
            String actionCode, long id, String username, String description,
            String remarks, String ipAddress) {
        jdbcTemplate.update("""
                INSERT INTO dms.app_audit (
                    action_type, wflow_type, wflow_code, action_by,
                    action_description, remarks, ip_address
                ) VALUES (?, ?, ?, ?, ?, ?, ?::inet)
                """, actionCode, AUDIT_WORKFLOW_TYPE, Long.toString(id), username,
                description, remarks, ipAddress);
    }

    private MetadataMaintenanceAuditItem mapAudit(ResultSet rs) throws SQLException {
        return new MetadataMaintenanceAuditItem(
                rs.getLong("audit_id"),
                rs.getString("action_type"),
                rs.getString("wflow_type"),
                rs.getString("wflow_code"),
                rs.getString("action_by"),
                rs.getObject("action_at", OffsetDateTime.class),
                rs.getString("action_description"),
                rs.getString("remarks")
        );
    }

    private static boolean activeValue(MetadataMaintenanceInput input) {
        return input.isActive() == null || input.isActive();
    }

    private static boolean activeValue(MetadataMaintenanceInput input, boolean currentValue) {
        return input.isActive() == null ? currentValue : input.isActive();
    }

    private static void requireEditable(MetadataMaintenanceRecord record) {
        if ("D".equals(record.status())) {
            throw new IllegalStateException("Deleted metadata cannot be edited");
        }
        if ("I".equals(record.status())) {
            throw new IllegalStateException("Draft metadata must be submitted explicitly");
        }
        if ("P".equals(record.status())) {
            throw new IllegalStateException("Pending metadata cannot be edited");
        }
    }

    private static void requireDraft(MetadataMaintenanceRecord record) {
        if (!"I".equals(record.status())) {
            throw new IllegalStateException("Metadata record is not a draft");
        }
    }

    private static void requirePending(MetadataMaintenanceRecord record) {
        if (!"P".equals(record.status())) {
            throw new IllegalStateException("Metadata record is not pending approval");
        }
    }

    private static void requireDifferentReviewer(MetadataMaintenanceRecord record, String username) {
        String submitter = record.updatedBy() == null ? record.createdBy() : record.updatedBy();
        if (submitter.equalsIgnoreCase(username)) {
            throw new IllegalStateException("The creator cannot approve or reject their own metadata");
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

    private static String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireOneRow(int count, String message) {
        if (count != 1) {
            throw new IllegalStateException(message);
        }
    }
}
