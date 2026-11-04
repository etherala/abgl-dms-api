package com.abgl.dms.repository;

import com.abgl.dms.documentsearch.DocumentSearchItem;
import com.abgl.dms.documentsearch.DocumentSearchRequest;
import com.abgl.dms.documentsearch.ApprovalDocumentSearchRequest;
import com.abgl.dms.documentsearch.StoredDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DocumentSearchRepository {

    private static final String FROM_AND_ACCESS = """
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
                WHERE uev.entity_code = dm.reference_code
                  AND uev.user_code = du.username
            )
            """;

    private static final String SELECT_DOCUMENTS = """
            SELECT DISTINCT
                   dt.document_type_id,
                   dt.document_type_code,
                   dt.approval_type,
                   dt.critical_indicator,
                   dt.expiry_date_mandatory,
                   dm.image_id,
                   dm.version,
                   dm.reference_code,
                   dm.document_date,
                   dm.expiry_date,
                   dm.title,
                   dm.additional_information,
                   dm.approval_status,
                   dm.created_by AS uploaded_by,
                   dm.created_datetime AS uploaded_on,
                   dm.doc_store_path,
                   dm.doc_file_name,
                   dm.doc_size,
                   dm.doc_actual_file_name,
                   em.entity_name,
                   dt.document_type_name,
                   dt.retention_period_value,
                   dt.master_retention_code,
                   dt.physical_storage_required,
                   amd.md_desc AS category_description,
                   (CURRENT_DATE - dm.created_datetime::date)::int AS day_pending
            """ + FROM_AND_ACCESS;

    private static final String COUNT_DOCUMENTS =
            "SELECT COUNT(DISTINCT dm.document_id) " + FROM_AND_ACCESS;

    private static final String FIND_VISIBLE_DOCUMENT_FILE = """
            SELECT dm.image_id,
                   dm.version,
                   dm.doc_store_path,
                   dm.doc_file_name,
                   dm.doc_actual_file_name,
                   dm.doc_extension
            FROM dms.document_master dm
            JOIN dms.document_type_master dt
              ON dt.document_type_code = dm.document_type
            JOIN dms.dms_user du
              ON LOWER(du.username) = LOWER(?)
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

    private final JdbcTemplate jdbcTemplate;
    private final int approvalDays;

    public DocumentSearchRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${document.approval.days:3}") int approvalDays) {
        this.jdbcTemplate = jdbcTemplate;
        if (approvalDays < 0) {
            throw new IllegalArgumentException("Document approval days must not be negative");
        }
        this.approvalDays = approvalDays;
    }

    public long countVisibleDocuments(String username, DocumentSearchRequest request) {
        QueryParts query = filterQuery(username, request);
        Long count = jdbcTemplate.queryForObject(
                COUNT_DOCUMENTS + query.whereClause(), Long.class, query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public List<DocumentSearchItem> searchVisibleDocuments(
            String username, DocumentSearchRequest request, int pageSize, long offset) {
        return searchDocuments(username, request, null, pageSize, offset, SELECT_DOCUMENTS);
    }

    public long countVisibleApprovalDocuments(String username, ApprovalDocumentSearchRequest request) {
        QueryParts query = approvalFilterQuery(username, request);
        Long count = jdbcTemplate.queryForObject(
                COUNT_DOCUMENTS + query.whereClause(), Long.class, query.parameters().toArray());
        return count == null ? 0 : count;
    }

    public List<DocumentSearchItem> searchVisibleApprovalDocuments(
            String username, ApprovalDocumentSearchRequest request, int pageSize, long offset) {
        return searchDocuments(
                username,
                toDocumentSearchRequest(request),
                request.submittedBy(),
                pageSize,
                offset,
                SELECT_APPROVAL_DOCS);
    }

    private List<DocumentSearchItem> searchDocuments(
            String username,
            DocumentSearchRequest request,
            String submittedBy,
            int pageSize,
            long offset,
            String selectQuery) {
        QueryParts query = filterQuery(username, request, submittedBy);
        List<Object> parameters = new ArrayList<>(query.parameters());
        parameters.add(pageSize);
        parameters.add(offset);
        return jdbcTemplate.query(
                selectQuery + query.whereClause() + "\n"
                        + """
                          ORDER BY dm.created_datetime DESC, dm.image_id DESC, dm.version DESC
                          LIMIT ? OFFSET ?
                          """,
                (resultSet, rowNumber) -> new DocumentSearchItem(
                        resultSet.getLong("document_type_id"),
                        resultSet.getString("document_type_code"),
                        resultSet.getString("approval_type"),
                        resultSet.getBoolean("critical_indicator"),
                        resultSet.getBoolean("expiry_date_mandatory"),
                        resultSet.getLong("image_id"),
                        resultSet.getInt("version"),
                        resultSet.getString("reference_code"),
                        resultSet.getObject("document_date", java.time.LocalDate.class),
                        resultSet.getObject("expiry_date", java.time.LocalDate.class),
                        resultSet.getString("title"),
                        resultSet.getString("additional_information"),
                        resultSet.getString("approval_status"),
                        resultSet.getString("uploaded_by"),
                        resultSet.getObject("uploaded_on", java.time.OffsetDateTime.class),
                        resultSet.getString("doc_store_path"),
                        resultSet.getString("doc_file_name"),
                        resultSet.getObject("doc_size", Long.class),
                        resultSet.getString("doc_actual_file_name"),
                        resultSet.getString("entity_name"),
                        resultSet.getString("document_type_name"),
                        resultSet.getBigDecimal("retention_period_value"),
                        resultSet.getString("master_retention_code"),
                        resultSet.getBoolean("physical_storage_required"),
                        resultSet.getString("category_description"),
                        resultSet.getInt("day_pending"),
                        resultSet.getInt("day_pending") > approvalDays
                ),
                parameters.toArray());
    }

    private static final String SELECT_APPROVAL_DOCS = SELECT_DOCUMENTS;

    public java.util.Optional<StoredDocument> findVisibleDocumentFile(
            String username, long imageId, int version) {
        List<StoredDocument> documents = jdbcTemplate.query(
                FIND_VISIBLE_DOCUMENT_FILE,
                (resultSet, rowNumber) -> new StoredDocument(
                        resultSet.getLong("image_id"),
                        resultSet.getInt("version"),
                        resultSet.getString("doc_store_path"),
                        resultSet.getString("doc_file_name"),
                        resultSet.getString("doc_actual_file_name"),
                        resultSet.getString("doc_extension")
                ),
                username, imageId, version);
        return documents.stream().findFirst();
    }

    private QueryParts filterQuery(String username, DocumentSearchRequest request) {
        return filterQuery(username, request, null);
    }

    private QueryParts approvalFilterQuery(String username, ApprovalDocumentSearchRequest request) {
        QueryParts query = filterQuery(username, toDocumentSearchRequest(request), request.submittedBy());
        return query;
    }

    private QueryParts filterQuery(
            String username, DocumentSearchRequest request, String submittedBy) {
        StringBuilder where = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        parameters.add(username);

        addExactFilter(where, parameters, "dm.reference_code", request.referenceCode());
        addExactFilter(where, parameters, "dt.document_category", request.categoryCode());
        addExactFilter(where, parameters, "dt.document_type_code", request.documentTypeCode());
        addTitleFilter(where, parameters, request.title());
        addUploadDateRange(where, parameters, request.uploadDateFrom(), request.uploadDateTo());
        addDateRange(where, parameters, "dm.document_date",
                request.documentDateFrom(), request.documentDateTo());
        addDateRange(where, parameters, "dm.expiry_date",
                request.expiryDateFrom(), request.expiryDateTo());
        addExactFilter(where, parameters, "dm.approval_status", request.status());
        addCaseInsensitiveExactFilter(where, parameters, "dm.created_by", submittedBy);
        return new QueryParts(where.toString(), parameters);
    }

    private void addCaseInsensitiveExactFilter(
            StringBuilder where, List<Object> parameters, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND LOWER(").append(column).append(") = LOWER(?)");
            parameters.add(value.trim());
        }
    }

    private DocumentSearchRequest toDocumentSearchRequest(ApprovalDocumentSearchRequest request) {
        return new DocumentSearchRequest(
                request.referenceCode(),
                request.categoryCode(),
                request.documentTypeCode(),
                request.title(),
                request.uploadDateFrom(),
                request.uploadDateTo(),
                request.documentDateFrom(),
                request.documentDateTo(),
                request.expiryDateFrom(),
                request.expiryDateTo(),
                request.statusCode(),
                request.page(),
                request.pageSize());
    }

    private void addExactFilter(
            StringBuilder where, List<Object> parameters, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            parameters.add(value.trim());
        }
    }

    private void addTitleFilter(StringBuilder where, List<Object> parameters, String title) {
        if (title != null && !title.isBlank()) {
            where.append(" AND dm.title ILIKE ? ESCAPE '\\'");
            parameters.add("%" + escapeLikePattern(title.trim()) + "%");
        }
    }

    private void addUploadDateRange(
            StringBuilder where, List<Object> parameters, java.time.LocalDate from, java.time.LocalDate to) {
        if (from != null) {
            where.append(" AND dm.created_datetime >= ?::date");
            parameters.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND dm.created_datetime < (?::date + INTERVAL '1 day')");
            parameters.add(Date.valueOf(to));
        }
    }

    private void addDateRange(
            StringBuilder where,
            List<Object> parameters,
            String column,
            java.time.LocalDate from,
            java.time.LocalDate to) {
        if (from != null) {
            where.append(" AND ").append(column).append(" >= ?");
            parameters.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND ").append(column).append(" <= ?");
            parameters.add(Date.valueOf(to));
        }
    }

    private String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private record QueryParts(String whereClause, List<Object> parameters) {}
}
