package com.abgl.dms.repository;

import com.abgl.dms.documentupload.AuthorizedDocumentType;
import com.abgl.dms.documentupload.DocumentRecord;
import com.abgl.dms.documentupload.UploadUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
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
            ) VALUES ('I', 'DOC_UPLOAD', ?, ?, 'Uploaded document',
                      'User uploaded document successfully.', ?::inet)
            """;

    private static final String INSERT_APPROVAL_AUDIT = """
            INSERT INTO dms.app_audit (
                action_type,
                wflow_type,
                wflow_code,
                action_by,
                action_description,
                remarks,
                ip_address
            ) VALUES ('A', 'DOC_UPLOAD', ?, ?, 'Approved document',
                      'Document auto-approved on upload.', ?::inet)
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

    public long insertDocument(DocumentRecord document) {
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
                Long.toString(document.imageId()),
                document.createdBy(),
                document.ipAddress());
        if ("A".equals(document.approvalStatus())) {
            jdbcTemplate.update(
                    INSERT_APPROVAL_AUDIT,
                    Long.toString(document.imageId()),
                    document.createdBy(),
                    document.ipAddress());
        }
        return documentId;
    }

}
