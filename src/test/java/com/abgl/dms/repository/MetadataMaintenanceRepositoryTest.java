package com.abgl.dms.repository;

import com.abgl.dms.metadata.MetadataMaintenanceRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import com.abgl.dms.metadata.MetadataMaintenanceSearch;
import java.util.List;

class MetadataMaintenanceRepositoryTest {

    @Test
    void pendingQuerySeparatesSelectFromWorkbenchColumns() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MetadataMaintenanceRepository repository = new MetadataMaintenanceRepository(jdbcTemplate);

        repository.findPending();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), ArgumentMatchers.<RowMapper<MetadataMaintenanceRecord>>any());
        assertTrue(sqlCaptor.getValue().startsWith("SELECT wb_id,"));
    }

    @Test
    void searchProjectsAndFiltersParentCode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(
                any(String.class),
                ArgumentMatchers.<RowMapper<MetadataMaintenanceRecord>>any(),
                any(Object[].class))).thenReturn(List.of());
        MetadataMaintenanceRepository repository = new MetadataMaintenanceRepository(jdbcTemplate);
        MetadataMaintenanceSearch search = new MetadataMaintenanceSearch(
                null, null, null, null, "BRANCH-01", null, null, null, 0, 25);

        repository.search(search, 0, 25);

        ArgumentCaptor<String> countCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                countCaptor.capture(), eq(Long.class), any(Object[].class));
        assertTrue(countCaptor.getValue().contains("md_parent_code"));

        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                resultCaptor.capture(),
                ArgumentMatchers.<RowMapper<MetadataMaintenanceRecord>>any(),
                any(Object[].class));
        assertTrue(resultCaptor.getValue().contains("LOWER(md_parent_code) LIKE LOWER(?)"));
    }
}
