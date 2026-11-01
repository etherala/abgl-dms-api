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
                   dt.approval_type,
                   dt.critical_indicator,
                   dt.expiry_date_mandatory
            FROM dms.dms_user du
            JOIN dms.user_document_collection udc ON udc.user_id = du.user_id
            JOIN dms.document_collection_document_type dcdt
              ON dcdt.document_collection_id = udc.document_collection_id
            JOIN dms.document_type dt ON dt.document_type_id = dcdt.document_type_id
            WHERE LOWER(du.username) = LOWER(?)
              AND dt.document_type_code = ?
              AND dt.document_category = ?
              AND dt.is_active = TRUE
              AND udc.document_collection_id = ?
            LIMIT 1
            """;

    private static final String HAS_ENTITY_VISIBILITY = """
            SELECT COUNT(*)
            FROM dms.user_entity_visibility
            WHERE user_code = ?
              AND entity_code = ?
            """;

    private static final String NEXT_IMAGE_ID = "SELECT nextval('dms.doc_image_id_seq')";

    private static final String INSERT_DOCUMENT = """
            INSERT INTO dms."document" (
                image_id,
                document_collection_id,
                document_type_id,
                folder_id,
                reference_type,
                reference_code,
                document_date,
                expiry_date,
                title,
                additional_information,
                current_version_number,
                approval_status,
                is_superseded,
                is_archived,
                created_by,
                entity_code
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1.0, 'Pending', FALSE, FALSE, ?, ?)
            RETURNING document_id
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
            String username, String documentTypeCode, String categoryCode, long documentCollectionId) {
        List<AuthorizedDocumentType> documentTypes = jdbcTemplate.query(
                FIND_AUTHORIZED_DOCUMENT_TYPE,
                (resultSet, rowNumber) -> new AuthorizedDocumentType(
                        resultSet.getLong("document_type_id"),
                        resultSet.getString("approval_type"),
                        resultSet.getBoolean("critical_indicator"),
                        resultSet.getBoolean("expiry_date_mandatory")
                ),
                username, documentTypeCode, categoryCode, documentCollectionId);
        return documentTypes.stream().findFirst();
    }

    public boolean hasEntityVisibility(String userCode, String referenceCode) {
        Long count = jdbcTemplate.queryForObject(
                HAS_ENTITY_VISIBILITY, Long.class, userCode, referenceCode);
        return count != null && count > 0;
    }

    public String nextImageId() {
        Long id = jdbcTemplate.queryForObject(NEXT_IMAGE_ID, Long.class);
        if (id == null) {
            throw new IllegalStateException("Image ID sequence returned no value");
        }
        return id.toString();
    }

    public long insertDocument(DocumentRecord document) {
        Long documentId = jdbcTemplate.queryForObject(
                INSERT_DOCUMENT,
                Long.class,
                document.imageId(),
                document.documentCollectionId(),
                document.documentTypeId(),
                document.folderId(),
                document.referenceType(),
                document.referenceCode(),
                document.documentDate(),
                document.expiryDate(),
                document.title(),
                document.additionalInformation(),
                document.createdBy(),
                document.entityCode()
        );
        if (documentId == null) {
            throw new IllegalStateException("Document insert returned no document ID");
        }
        return documentId;
    }

}
