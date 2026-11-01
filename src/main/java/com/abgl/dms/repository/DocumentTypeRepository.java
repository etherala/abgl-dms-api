package com.abgl.dms.repository;

import com.abgl.dms.documenttype.DocumentType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DocumentTypeRepository {

    private static final String FIND_ACTIVE_BY_CATEGORY = """
            SELECT document_type_code,
                   document_category,
                   document_type_name,
                   approval_type,
                   critical_indicator,
                   description,
                   expiry_date_mandatory,
                   is_active
            FROM dms.document_type
            WHERE document_category = ?
              AND is_active = TRUE
            ORDER BY document_type_name ASC, document_type_code ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentTypeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DocumentType> findActiveByCategory(String categoryCode) {
        return jdbcTemplate.query(FIND_ACTIVE_BY_CATEGORY, (resultSet, rowNumber) ->
                new DocumentType(
                        resultSet.getString("document_type_code"),
                        resultSet.getString("document_category"),
                        resultSet.getString("document_type_name"),
                        resultSet.getString("approval_type"),
                        resultSet.getBoolean("critical_indicator"),
                        resultSet.getString("description"),
                        resultSet.getBoolean("expiry_date_mandatory"),
                        resultSet.getBoolean("is_active")
                ), categoryCode);
    }
}
