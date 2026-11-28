package com.abgl.dms.repository;

import com.abgl.dms.documentsearch.DocumentSearchRequest;
import com.abgl.dms.documentsearch.ApprovalDocumentSearchRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void expiryTrackingCountAndResultsExcludeNullExpiryDatesOnlyForExpirySearch() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(java.util.List.of());
        DocumentSearchRepository repository = new DocumentSearchRepository(jdbcTemplate, 3, 30, 90);
        DocumentSearchRequest request = new DocumentSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null,
                1, 10, null, null);

        repository.countVisibleExpiryTrackingDocuments("admin", request);
        repository.searchVisibleExpiryTrackingDocuments("admin", request, 10, 0);

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                countSqlCaptor.capture(), eq(Long.class), any(Object[].class));
        assertTrue(countSqlCaptor.getValue().contains("AND dm.expiry_date IS NOT NULL"));

        ArgumentCaptor<String> resultsSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                resultsSqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(resultsSqlCaptor.getValue().contains("AND dm.expiry_date IS NOT NULL"));

        repository.countVisibleDocuments("admin", request);
        ArgumentCaptor<String> generalSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForObject(
                generalSqlCaptor.capture(), eq(Long.class), any(Object[].class));
        assertFalse(generalSqlCaptor.getAllValues().get(1).contains("AND dm.expiry_date IS NOT NULL"));
    }

    @Test
    void approvalDayPendingUsesUpdatedDatetimeThenCreatedDatetime() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class))).thenReturn(java.util.List.of());
        DocumentSearchRepository repository = new DocumentSearchRepository(jdbcTemplate, 3, 30, 90);
        ApprovalDocumentSearchRequest request = new ApprovalDocumentSearchRequest(
                null, null, null, null, null, null,
                null, null, null, null, null, null, 1, 25);

        repository.searchVisibleApprovalDocuments("admin", request, 25, 0);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains(
                "COALESCE(dm.updated_datetime, dm.created_datetime)::date"));
        assertTrue(sqlCaptor.getValue().contains(
                "GREATEST(0,"));
    }
}
