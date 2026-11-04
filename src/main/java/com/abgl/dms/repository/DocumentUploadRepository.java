package com.abgl.dms.repository;

import com.abgl.dms.documentupload.AuthorizedDocumentType;
import com.abgl.dms.documentupload.DocumentApprovalRecord;
import com.abgl.dms.documentupload.DocumentRecord;
import com.abgl.dms.documentupload.DocumentMaintenanceRecord;
import com.abgl.dms.documentupload.DocumentNotAccessibleException;
import com.abgl.dms.documentupload.DocumentStorageRecord;
import com.abgl.dms.documentupload.UploadUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentUploadRepository {

    private static final String FIND_UPLOAD_USER = """
            SELECT user_id, username
            FROM dms.dms_user
            WHERE LOWER(username) = LOWER(?)
            """;

    private static final String FIND_AUTHORIZED_DOCUMENT_TYPE = """
            SELECT dt.document_type_id,
                   dt.document_type_code,
                   dt.approval_type,
                   dt.critical_indicator,
                   dt.expiry_date_mandatory
            FROM dms.dms_user du
            JOIN dms.user_document_collection udc ON udc.user_id = du.user_id
            JOIN dms.doc_type_collection_mapping dtcm
              ON dtcm.collection_id = udc.collection_id
            JOIN dms.document_type_master dt ON dt.document_type_code = dtcm.document_type
            WHERE LOWER(du.username) = LOWER(?)
              AND dt.document_type_code = ?
              AND dt.document_category = ?
              AND dt.is_active = TRUE
            LIMIT 1
            """;

    private static final String HAS_ENTITY_VISIBILITY = """
            SELECT COUNT(*)
            FROM dms.user_entity_visibility
            WHERE user_code = ?
              AND entity_code = ?
            """;

    private static final String NEXT_IMAGE_ID = """
            SELECT nextval(pg_get_serial_sequence('dms.document_master', 'image_id'))
            """;

    private static final String INSERT_DOCUMENT = """
            INSERT INTO dms.document_master (
                image_id,
                document_type,
                reference_code,
                document_date,
                expiry_date,
                title,
                additional_information,
                version,
                approval_status,
                is_archived,
                created_by,
                doc_store_path,
                doc_file_name,
                doc_actual_file_name,
                doc_extension,
                doc_size,
                doc_hash,
                approved_by,
                approved_datetime
            ) OVERRIDING SYSTEM VALUE
            VALUES (?, ?, ?, CAST(? AS DATE), CAST(? AS DATE), ?, ?, ?, ?, FALSE, ?, ?, ?, ?, ?, ?, ?,
                    CASE WHEN ? = 'A' THEN ? ELSE NULL END,
                    CASE WHEN ? = 'A' THEN CURRENT_TIMESTAMP ELSE NULL END)
            RETURNING document_id
            """;

    private static final String INSERT_UPLOAD_AUDIT = """
            INSERT INTO dms.app_audit (
                action_type,
                wflow_type,
                wflow_code,
                action_by,
                action_description,
                remarks,
                ip_address
            ) VALUES (?, 'DOC_UPLOAD', ?, ?, ?, ?, ?::inet)
            """;

    private static final String LOCK_VISIBLE_DOCUMENT = """
            SELECT dm.document_id
            FROM dms.document_master dm
            JOIN dms.document_type_master dt
              ON dt.document_type_code = dm.document_type
            JOIN dms.dms_user du
              ON LOWER(du.username) = LOWER(?)
            JOIN dms.entity_master em
              ON em.entity_code = dm.reference_code
            JOIN dms.app_metadata_droplist amd
              ON amd.md_code = dt.document_category
             AND amd.md_type = 'DOC_CATEGORY'
            WHERE dm.image_id = ?
              AND dm.version = ?
              AND EXISTS (
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
                  WHERE uev.entity_code = dm.reference_code
                    AND uev.user_code = du.username
              )
            FOR UPDATE OF dm
            """;

    private static final String LOCK_VISIBLE_DRAFT_DOCUMENT = LOCK_VISIBLE_DOCUMENT
            .replace("FOR UPDATE OF dm", "AND dm.approval_status = 'I'\n            FOR UPDATE OF dm");
    private static final String LOCK_VISIBLE_PENDING_DOCUMENT = LOCK_VISIBLE_DOCUMENT
            .replace("FOR UPDATE OF dm", "AND dm.approval_status = 'P'\n            FOR UPDATE OF dm");

    private static final String FIND_LATEST_VISIBLE_VERSION = """
            SELECT MAX(dm.version)
            FROM dms.document_master dm
            JOIN dms.document_type_master dt
              ON dt.document_type_code = dm.document_type
            JOIN dms.dms_user du
              ON LOWER(du.username) = LOWER(?)
            JOIN dms.entity_master em
              ON em.entity_code = dm.reference_code
            JOIN dms.app_metadata_droplist amd
              ON amd.md_code = dt.document_category
             AND amd.md_type = 'DOC_CATEGORY'
            WHERE dm.image_id = ?
              AND EXISTS (
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
                  WHERE uev.entity_code = dm.reference_code
                    AND uev.user_code = du.username
              )
            """;

    private static final String INSERT_DOCUMENT_AUDIT = """
            INSERT INTO dms.document_audit (
                document_id, image_id, document_type, reference_code,
                document_date, expiry_date, title, additional_information,
                version, approval_status, is_archived, created_by, created_datetime,
                updated_by, updated_datetime, approved_by, approved_datetime,
                doc_store_path, doc_file_name, doc_extension, doc_size, doc_hash,
                audit_date, doc_actual_file_name
            )
            SELECT document_id, image_id, document_type, reference_code,
                   document_date, expiry_date, title, additional_information,
                   version,
                   approval_status,
                   is_archived, created_by, created_datetime,
                   updated_by, updated_datetime, approved_by, approved_datetime,
                   doc_store_path, doc_file_name, doc_extension, doc_size, doc_hash,
                   CURRENT_TIMESTAMP, doc_actual_file_name
            FROM dms.document_master
            WHERE image_id = ? AND version = ?
            """;

    private static final String INSERT_DOCUMENT_HISTORY = """
            INSERT INTO dms.document_hist (
                document_id, image_id, document_type, reference_code,
                document_date, expiry_date, title, additional_information,
                version, approval_status, is_archived, created_by, created_datetime,
                updated_by, updated_datetime, approved_by, approved_datetime,
                doc_store_path, doc_file_name, doc_extension, doc_size, doc_hash,
                history_date, doc_actual_file_name
            )
            SELECT document_id, image_id, document_type, reference_code,
                   document_date, expiry_date, title, additional_information,
                   version, approval_status, is_archived, created_by, created_datetime,
                   updated_by, updated_datetime, approved_by, approved_datetime,
                   doc_store_path, doc_file_name, doc_extension, doc_size, doc_hash,
                   CURRENT_TIMESTAMP, doc_actual_file_name
            FROM dms.document_master
            WHERE image_id = ? AND version = ?
            """;

    private static final String INSERT_DOCUMENT_ARCHIVED = """
            INSERT INTO dms.document_archived (
                document_id, image_id, document_type, reference_code,
                document_date, expiry_date, title, additional_information,
                version, approval_status, is_archived, created_by, created_datetime,
                updated_by, updated_datetime, approved_by, approved_datetime,
                doc_store_path, doc_file_name, doc_extension, doc_size, doc_hash,
                archive_date, doc_actual_file_name
            )
            SELECT document_id, image_id, document_type, reference_code,
                   document_date, expiry_date, title, additional_information,
                   version, approval_status, TRUE, created_by, created_datetime,
                   updated_by, updated_datetime, approved_by, approved_datetime,
                   doc_store_path, doc_file_name, doc_extension, doc_size, doc_hash,
                   CURRENT_TIMESTAMP, doc_actual_file_name
            FROM dms.document_master
            WHERE image_id = ? AND version = ?
            """;

    private static final String UPDATE_DOCUMENT_METADATA = """
            UPDATE dms.document_master
            SET document_type = ?,
                reference_code = ?,
                document_date = CAST(? AS DATE),
                expiry_date = CAST(? AS DATE),
                title = ?,
                additional_information = ?,
                approval_status = CASE
                    WHEN CAST(? AS VARCHAR) IS NOT NULL THEN CAST(? AS VARCHAR)
                    ELSE approval_status
                END,
                approved_by = CASE
                    WHEN CAST(? AS VARCHAR) = 'A' THEN ?
                    WHEN CAST(? AS VARCHAR) IS NOT NULL THEN NULL
                    ELSE approved_by
                END,
                approved_datetime = CASE
                    WHEN CAST(? AS VARCHAR) = 'A' THEN CURRENT_TIMESTAMP
                    WHEN CAST(? AS VARCHAR) IS NOT NULL THEN NULL
                    ELSE approved_datetime
                END,
                updated_by = ?,
                updated_datetime = CURRENT_TIMESTAMP
            WHERE image_id = ? AND version = ?
            """;

    private static final String UPDATE_DOCUMENT_VERSION = """
            UPDATE dms.document_master
            SET document_type = ?,
                reference_code = ?,
                document_date = CAST(? AS DATE),
                expiry_date = CAST(? AS DATE),
                title = ?,
                additional_information = ?,
                version = ?,
                doc_store_path = ?,
                doc_file_name = ?,
                doc_actual_file_name = ?,
                doc_extension = ?,
                doc_size = ?,
                doc_hash = ?,
                approval_status = ?,
                approved_by = CASE WHEN ? = 'A' THEN ? ELSE NULL END,
                approved_datetime = CASE WHEN ? = 'A' THEN CURRENT_TIMESTAMP ELSE NULL END,
                updated_by = ?,
                updated_datetime = CURRENT_TIMESTAMP
            WHERE image_id = ? AND version = ?
            """;

    private static final String DELETE_DOCUMENT = """
            DELETE FROM dms.document_master
            WHERE image_id = ? AND version = ?
            """;

    private static final String FIND_VISIBLE_STORAGE_RECORD = """
            SELECT dm.image_id, dm.version, dm.doc_store_path, dm.doc_file_name
            FROM dms.document_master dm
            JOIN dms.document_type_master dt
              ON dt.document_type_code = dm.document_type
            JOIN dms.dms_user du
              ON LOWER(du.username) = LOWER(?)
            JOIN dms.entity_master em
              ON em.entity_code = dm.reference_code
            JOIN dms.app_metadata_droplist amd
              ON amd.md_code = dt.document_category
             AND amd.md_type = 'DOC_CATEGORY'
            WHERE dm.image_id = ?
              AND dm.version = ?
              AND EXISTS (
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
                  WHERE uev.entity_code = dm.reference_code
                    AND uev.user_code = du.username
              )
            """;

    private static final String UPDATE_DOCUMENT_FILE = """
            UPDATE dms.document_master
            SET document_type = ?,
                reference_code = ?,
                document_date = CAST(? AS DATE),
                expiry_date = CAST(? AS DATE),
                title = ?,
                additional_information = ?,
                doc_store_path = ?,
                doc_file_name = ?,
                doc_actual_file_name = ?,
                doc_extension = ?,
                doc_size = ?,
                doc_hash = ?,
                approval_status = ?,
                approved_by = CASE WHEN ? = 'A' THEN ? ELSE NULL END,
                approved_datetime = CASE WHEN ? = 'A' THEN CURRENT_TIMESTAMP ELSE NULL END,
                updated_by = ?,
                updated_datetime = CURRENT_TIMESTAMP
            WHERE image_id = ? AND version = ? AND approval_status = 'I'
            """;

    private static final String INSERT_MAINTENANCE_AUDIT = """
            INSERT INTO dms.app_audit (
                action_type, wflow_type, wflow_code, action_by,
                action_description, remarks, ip_address
            ) VALUES (?, ?, ?, ?, ?, ?, ?::inet)
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentUploadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UploadUser> findUploadUser(String username) {
        List<UploadUser> users = jdbcTemplate.query(FIND_UPLOAD_USER, (resultSet, rowNumber) ->
                new UploadUser(
                        resultSet.getObject("user_id", UUID.class),
                        resultSet.getString("username")
                ), username);
        if (users.size() > 1) {
            throw new IllegalStateException("Multiple DMS user records match the authenticated username");
        }
        return users.stream().findFirst();
    }

    public Optional<AuthorizedDocumentType> findAuthorizedDocumentType(
            String username, String documentTypeCode, String categoryCode) {
        List<AuthorizedDocumentType> documentTypes = jdbcTemplate.query(
                FIND_AUTHORIZED_DOCUMENT_TYPE,
                (resultSet, rowNumber) -> new AuthorizedDocumentType(
                        resultSet.getLong("document_type_id"),
                        resultSet.getString("document_type_code"),
                        resultSet.getString("approval_type"),
                        resultSet.getBoolean("critical_indicator"),
                        resultSet.getBoolean("expiry_date_mandatory")
                ),
                username, documentTypeCode, categoryCode);
        return documentTypes.stream().findFirst();
    }

    public boolean hasEntityVisibility(String userCode, String referenceCode) {
        Long count = jdbcTemplate.queryForObject(
                HAS_ENTITY_VISIBILITY, Long.class, userCode, referenceCode);
        return count != null && count > 0;
    }

    public long nextImageId() {
        Long id = jdbcTemplate.queryForObject(NEXT_IMAGE_ID, Long.class);
        if (id == null) {
            throw new IllegalStateException("Image ID sequence returned no value");
        }
        return id;
    }

    public Optional<Integer> findLatestVisibleVersion(String username, long imageId) {
        Integer version = jdbcTemplate.queryForObject(
                FIND_LATEST_VISIBLE_VERSION, Integer.class, username, imageId);
        return Optional.ofNullable(version);
    }

    public long insertDocument(DocumentRecord document, String action) {
        Long documentId = jdbcTemplate.queryForObject(
                INSERT_DOCUMENT,
                Long.class,
                document.imageId(),
                document.documentType(),
                document.referenceCode(),
                document.documentDate(),
                document.expiryDate(),
                document.title(),
                document.additionalInformation(),
                document.version(),
                document.approvalStatus(),
                document.createdBy(),
                document.docStorePath(),
                document.docFileName(),
                document.docActualFileName(),
                document.docExtension(),
                document.docSize(),
                document.docHash(),
                document.approvalStatus(),
                document.createdBy(),
                document.approvalStatus()
        );
        if (documentId == null) {
            throw new IllegalStateException("Document insert returned no document ID");
        }
        jdbcTemplate.update(
                INSERT_UPLOAD_AUDIT,
                "I",
                Long.toString(document.imageId()),
                document.createdBy(),
                "DRAFT".equals(action) ? "Saved document draft" : "Uploaded document",
                "DRAFT".equals(action)
                        ? "Document saved as Draft."
                        : "User uploaded document successfully.",
                document.ipAddress());
        if ("A".equals(document.approvalStatus()) && !"DRAFT".equals(action)) {
            insertWorkflowAudit(
                    "A", document.imageId(), document.createdBy(), "DOC_UPLOAD",
                    "Approved document", "Document auto-approved on upload.", document.ipAddress());
        }
        return documentId;
    }

    public Optional<DocumentStorageRecord> findVisibleStorageRecord(
            String username, long imageId, int version) {
        List<DocumentStorageRecord> records = jdbcTemplate.query(
                FIND_VISIBLE_STORAGE_RECORD,
                (resultSet, rowNumber) -> new DocumentStorageRecord(
                        resultSet.getLong("image_id"),
                        resultSet.getInt("version"),
                        resultSet.getString("doc_store_path"),
                        resultSet.getString("doc_file_name")),
                username, imageId, version);
        return records.stream().findFirst();
    }

    public void replaceDraftDocument(
            DocumentMaintenanceRecord document,
            String relativePath,
            String storedFileName,
            String actualFileName,
            String extension,
            long fileSize,
            String fileHash,
            String approvalStatus) {
        lockVisibleDocument(
                document.updatedBy(), document.imageId(), document.currentVersion(), true);
        int updatedRows = jdbcTemplate.update(
                UPDATE_DOCUMENT_FILE,
                document.documentTypeCode(),
                document.referenceCode(),
                document.documentDate(),
                document.expiryDate(),
                document.title(),
                document.additionalInformation(),
                relativePath,
                storedFileName,
                actualFileName,
                extension,
                fileSize,
                fileHash,
                approvalStatus,
                approvalStatus,
                document.updatedBy(),
                approvalStatus,
                document.updatedBy(),
                document.imageId(),
                document.currentVersion());
        requireOneRow(updatedRows, "Draft document was not replaced");
        insertWorkflowAudit(
                "U", document.imageId(), document.updatedBy(), "DOC_UPLOAD",
                "Replaced draft document file", "Draft document file replaced successfully.",
                document.ipAddress());
        if ("A".equals(approvalStatus)) {
            insertWorkflowAudit(
                    "A", document.imageId(), document.updatedBy(), "DOC_UPLOAD",
                    "Approved document", "Document auto-approved on draft replacement.",
                    document.ipAddress());
        }
    }

    public void deleteDocument(String username, long imageId, int version, String ipAddress) {
        lockVisibleDocument(username, imageId, version);
        insertWorkflowAudit(
                "D", imageId, username, "DOC_UPLOAD",
                "Deleted document", "User deleted document.", ipAddress);
        int deletedRows = jdbcTemplate.update(DELETE_DOCUMENT, imageId, version);
        requireOneRow(deletedRows, "Document was not deleted");
    }

    private void insertWorkflowAudit(
            String actionType,
            long imageId,
            String username,
            String workflowType,
            String description,
            String remarks,
            String ipAddress) {
        jdbcTemplate.update(
                INSERT_UPLOAD_AUDIT,
                actionType,
                Long.toString(imageId),
                username,
                description,
                remarks,
                ipAddress);
    }

    public void editOrReindexDocument(DocumentMaintenanceRecord document, String approvalStatus) {
        lockVisibleDocument(document.updatedBy(), document.imageId(), document.currentVersion());
        int archivedRows = jdbcTemplate.update(
                INSERT_DOCUMENT_AUDIT, document.imageId(), document.currentVersion());
        requireOneRow(archivedRows, "Document audit snapshot was not created");
        int updatedRows = jdbcTemplate.update(
                UPDATE_DOCUMENT_METADATA,
                document.documentTypeCode(),
                document.referenceCode(),
                document.documentDate(),
                document.expiryDate(),
                document.title(),
                document.additionalInformation(),
                approvalStatus,
                approvalStatus,
                approvalStatus,
                document.updatedBy(),
                approvalStatus,
                approvalStatus,
                approvalStatus,
                document.updatedBy(),
                document.imageId(),
                document.currentVersion());
        requireOneRow(updatedRows, "Document metadata was not updated");
        insertMaintenanceAudit(document);
        if ("A".equals(approvalStatus)) {
            insertMaintenanceAudit(
                    "A",
                    document.imageId(),
                    document.updatedBy(),
                    document.workflowType(),
                    "Approved document",
                    "Document auto-approved during metadata update.",
                    document.ipAddress());
        }
    }

    public void decideDocument(
            String username,
            DocumentApprovalRecord document,
            String approvalStatus,
            String ipAddress) {
        lockVisiblePendingDocument(username, document.imageId(), document.version());
        int auditRows = jdbcTemplate.update(
                INSERT_DOCUMENT_AUDIT, document.imageId(), document.version());
        requireOneRow(auditRows, "Document approval audit snapshot was not created");

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE dms.document_master
                SET approval_status = ?,
                    approved_by = ?,
                    approved_datetime = CURRENT_TIMESTAMP,
                    updated_by = ?,
                    updated_datetime = CURRENT_TIMESTAMP
                WHERE image_id = ? AND version = ? AND approval_status = 'P'
                """,
                approvalStatus,
                username,
                username,
                document.imageId(),
                document.version());
        requireOneRow(updatedRows, "Pending document was not updated");

        String action = "A".equals(approvalStatus) ? "Approved" : "Rejected";
        String remarks = document.remarks() == null || document.remarks().isBlank()
                ? "Document " + action.toLowerCase(Locale.ROOT) + "."
                : document.remarks().trim();
        insertMaintenanceAudit(
                approvalStatus,
                document.imageId(),
                username,
                "DOC_APPROVAL",
                action + " document",
                remarks,
                ipAddress);
    }

    public void createNewVersion(
            DocumentMaintenanceRecord document,
            int newVersion,
            String relativePath,
            String storedFileName,
            String actualFileName,
            String extension,
            long fileSize,
            String fileHash,
            String approvalStatus) {
        lockVisibleDocument(document.updatedBy(), document.imageId(), document.currentVersion());
        int historyRows = jdbcTemplate.update(
                INSERT_DOCUMENT_HISTORY, document.imageId(), document.currentVersion());
        requireOneRow(historyRows, "Document history snapshot was not created");
        int updatedRows = jdbcTemplate.update(
                UPDATE_DOCUMENT_VERSION,
                document.documentTypeCode(),
                document.referenceCode(),
                document.documentDate(),
                document.expiryDate(),
                document.title(),
                document.additionalInformation(),
                newVersion,
                relativePath,
                storedFileName,
                actualFileName,
                extension,
                fileSize,
                fileHash,
                approvalStatus,
                approvalStatus,
                document.updatedBy(),
                approvalStatus,
                document.updatedBy(),
                document.imageId(),
                document.currentVersion());
        requireOneRow(updatedRows, "Document version was not updated");
        insertMaintenanceAudit(document);
    }

    public void archiveDocument(String username, long imageId, int version, String ipAddress) {
        lockVisibleDocument(username, imageId, version);
        int archivedRows = jdbcTemplate.update(INSERT_DOCUMENT_ARCHIVED, imageId, version);
        requireOneRow(archivedRows, "Document archive snapshot was not created");
        int deletedRows = jdbcTemplate.update(DELETE_DOCUMENT, imageId, version);
        requireOneRow(deletedRows, "Document was not removed from active storage");
        insertMaintenanceAudit(
                "U",
                imageId, username, "DOC_ARCHIVE", "Archived document",
                "Document archived successfully.", ipAddress);
    }

    private void lockVisibleDocument(String username, long imageId, int version) {
        lockVisibleDocument(username, imageId, version, false);
    }

    private void lockVisibleDocument(String username, long imageId, int version, boolean draftOnly) {
        List<Long> matches = jdbcTemplate.query(
                draftOnly ? LOCK_VISIBLE_DRAFT_DOCUMENT : LOCK_VISIBLE_DOCUMENT,
                (resultSet, rowNumber) -> resultSet.getLong("document_id"),
                username, imageId, version);
        if (matches.isEmpty()) {
            throw new DocumentNotAccessibleException();
        }
        requireOneRow(matches.size(), "Document identity is not unique");
    }

    private void lockVisiblePendingDocument(String username, long imageId, int version) {
        List<Long> matches = jdbcTemplate.query(
                LOCK_VISIBLE_PENDING_DOCUMENT,
                (resultSet, rowNumber) -> resultSet.getLong("document_id"),
                username, imageId, version);
        if (matches.isEmpty()) {
            throw new DocumentNotAccessibleException();
        }
        requireOneRow(matches.size(), "Document identity is not unique");
    }

    private void insertMaintenanceAudit(DocumentMaintenanceRecord document) {
        insertMaintenanceAudit(
                "U",
                document.imageId(),
                document.updatedBy(),
                document.workflowType(),
                document.actionDescription(),
                document.remarks(),
                document.ipAddress());
    }

    private void insertMaintenanceAudit(
            String actionType,
            long imageId,
            String username,
            String workflowType,
            String description,
            String remarks,
            String ipAddress) {
        jdbcTemplate.update(
                INSERT_MAINTENANCE_AUDIT,
                actionType,
                workflowType,
                Long.toString(imageId),
                username,
                description,
                remarks,
                ipAddress);
    }

    private void requireOneRow(int count, String message) {
        if (count != 1) {
            throw new IllegalStateException(message);
        }
    }

}
