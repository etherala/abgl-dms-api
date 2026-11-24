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
}
