package com.abgl.dms.repository;

import com.abgl.dms.documentsearch.DocumentSearchRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentSearchRepositoryTest {

    @Test
    void filtersDocumentsUsingConfiguredExpiryStatusPeriods() {
        Map<String, String> conditions = Map.of(
                "DS", "(dm.expiry_date - CURRENT_DATE) > ? AND (dm.expiry_date - CURRENT_DATE) <= ?",
                "ES", "(dm.expiry_date - CURRENT_DATE) > 0 AND (dm.expiry_date - CURRENT_DATE) <= ?",
                "VE", "(dm.expiry_date - CURRENT_DATE) > ?",
                "NE", "dm.expiry_date IS NULL",
                "OE", "(dm.expiry_date - CURRENT_DATE) <= 0");

        conditions.forEach((status, expectedCondition) -> {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(0L);
            DocumentSearchRepository repository = new DocumentSearchRepository(jdbcTemplate, 3, 30, 90);
            DocumentSearchRequest request = new DocumentSearchRequest(
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, status);

            repository.countVisibleDocuments("admin", request);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).queryForObject(
                    sqlCaptor.capture(), eq(Long.class), any(Object[].class));
            assertTrue(sqlCaptor.getValue().contains(expectedCondition), status);
        });
    }

    @Test
    void rejectsExpiryAlertPeriodsWithMaximumBelowMinimum() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentSearchRepository(jdbcTemplate, 3, 31, 30));
    }
}
