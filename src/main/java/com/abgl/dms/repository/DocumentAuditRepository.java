package com.abgl.dms.repository;

import com.abgl.dms.documentaudit.DocumentAuditItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DocumentAuditRepository {

    private static final String HAS_DOCUMENT_VISIBILITY = """
            WITH requested_document AS (
                SELECT document_type, reference_code
                FROM dms.document_master
                WHERE image_id = ?
                UNION
                SELECT document_type, reference_code
                FROM dms.document_archived
                WHERE image_id = ?
            )
            SELECT COUNT(*)
            FROM requested_document rd
            JOIN dms.document_type_master dt
              ON dt.document_type_code = rd.document_type
            JOIN dms.dms_user du
              ON LOWER(du.username) = LOWER(?)
            JOIN dms.entity_master em
              ON em.entity_code = rd.reference_code
            JOIN dms.app_metadata_droplist amd
              ON amd.md_code = dt.document_category
             AND amd.md_type = 'DOC_CATEGORY'
            WHERE EXISTS (
                SELECT 1
                FROM dms.user_document_collection udc
                JOIN dms.doc_type_collection_mapping dtcm
                  ON dtcm.collection_id = udc.collection_id
                WHERE udc.user_id = du.user_id
                  AND dtcm.document_type = dt.document_type_code
            )
              AND EXISTS (
                SELECT 1
                FROM dms.user_entity_visibility uev
                WHERE uev.entity_code = rd.reference_code
                  AND uev.user_code = du.username
            )
            """;

    private static final String FIND_AUDIT = """
            SELECT audit_id,
                   action_type,
                   wflow_type,
                   wflow_code,
                   action_by,
                   action_at,
                   action_description,
                   remarks
            FROM dms.app_audit
            WHERE wflow_code = ?
            ORDER BY action_at DESC, audit_id DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isVisibleToUser(long imageId, String username) {
        Long count = jdbcTemplate.queryForObject(
                HAS_DOCUMENT_VISIBILITY, Long.class, imageId, imageId, username);
        return count != null && count > 0;
    }

    public List<DocumentAuditItem> findAudit(long imageId) {
        return jdbcTemplate.query(
                FIND_AUDIT,
                (resultSet, rowNumber) -> new DocumentAuditItem(
                        resultSet.getLong("audit_id"),
                        resultSet.getString("action_type"),
                        resultSet.getString("wflow_type"),
                        resultSet.getString("wflow_code"),
                        resultSet.getString("action_by"),
                        resultSet.getObject("action_at", java.time.OffsetDateTime.class),
                        resultSet.getString("action_description"),
                        resultSet.getString("remarks")
                ),
                Long.toString(imageId));
    }
}
