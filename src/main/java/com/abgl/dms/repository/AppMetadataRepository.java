package com.abgl.dms.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Repository
public class AppMetadataRepository {

    private static final String FIND_DROPLIST_METADATA = """
            SELECT md_type, md_code, md_desc
            FROM dms.app_metadata_droplist
            """;

    private final JdbcTemplate jdbcTemplate;

    public AppMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Map<String, String>> findDropListMetadata() {
        Map<String, Map<String, String>> valuesByType = new HashMap<>();
        jdbcTemplate.query(FIND_DROPLIST_METADATA, resultSet -> {
            String metadataType = Objects.requireNonNull(
                    resultSet.getString("md_type"), "Metadata md_type must not be null");
            String metadataCode = Objects.requireNonNull(
                    resultSet.getString("md_code"), "Metadata md_code must not be null");
            String metadataDescription = Objects.requireNonNull(
                    resultSet.getString("md_desc"), "Metadata md_desc must not be null");

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
