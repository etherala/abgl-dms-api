package com.abgl.dms.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Repository
public class AppMetadataRepository {

    private static final String FIND_DROPLIST_METADATA = """
            SELECT md_type AS metadata_type,
                   md_code AS metadata_code,
                   md_desc AS metadata_description
            FROM dms.app_metadata_droplist
            UNION ALL
            SELECT sd_type AS metadata_type,
                   sd_code AS metadata_code,
                   sd_desc AS metadata_description
            FROM dms.app_sysdata_droplist
            """;

    private final JdbcTemplate jdbcTemplate;

    public AppMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Map<String, String>> findDropListMetadata() {
        Map<String, Map<String, String>> valuesByType = new HashMap<>();
        jdbcTemplate.query(FIND_DROPLIST_METADATA, resultSet -> {
            String metadataType = Objects.requireNonNull(
                    resultSet.getString("metadata_type"), "Metadata type must not be null");
            String metadataCode = Objects.requireNonNull(
                    resultSet.getString("metadata_code"), "Metadata code must not be null");
            String metadataDescription = Objects.requireNonNull(
                    resultSet.getString("metadata_description"), "Metadata description must not be null");

            Map<String, String> valuesByCode = valuesByType.computeIfAbsent(
                    metadataType, ignored -> new HashMap<>());
            if (valuesByCode.containsKey(metadataCode)) {
                throw new IllegalStateException(
                        "Duplicate metadata code '" + metadataCode + "' for type '" + metadataType + "'");
            }
            valuesByCode.put(metadataCode, metadataDescription);
        });
        return valuesByType;
    }
}
