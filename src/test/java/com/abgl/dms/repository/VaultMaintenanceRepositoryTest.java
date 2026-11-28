package com.abgl.dms.repository;

import com.abgl.dms.vaultmaintenance.VaultMaintenanceRecord;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceSearch;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceInput;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceAuditItem;
import com.abgl.dms.vaultmaintenance.VaultHierarchyOption;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultMaintenanceRepositoryTest {

    @Test
    void pendingQueryStartsWithSeparatedSelectAndReturnsWorkbenchColumns() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VaultMaintenanceRepository repository = new VaultMaintenanceRepository(jdbcTemplate);

        repository.findPending();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                ArgumentMatchers.<RowMapper<VaultMaintenanceRecord>>any());
        assertTrue(sqlCaptor.getValue().startsWith("SELECT vault_id, wb_id,"));
        assertTrue(sqlCaptor.getValue().contains("FROM dms.vault_storage_wb"));
    }

    @Test
    void hierarchyChildrenAreScopedToActiveApprovedRecordsAndExactParent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VaultHierarchyOption branch = new VaultHierarchyOption("BRANCH-01", "Head office");
        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<VaultHierarchyOption>>any(),
                eq("VAULT_BRANCH"), eq("VAULT_LOCATION"), eq("LOC-01")))
                .thenReturn(List.of(branch));
        VaultMaintenanceRepository repository = new VaultMaintenanceRepository(jdbcTemplate);

        List<VaultHierarchyOption> items = repository.findHierarchyChildren(
                "VAULT_LOCATION", "LOC-01", "VAULT_BRANCH");

        assertTrue(items.contains(branch));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                ArgumentMatchers.<RowMapper<VaultHierarchyOption>>any(),
                eq("VAULT_BRANCH"), eq("VAULT_LOCATION"), eq("LOC-01"));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("md_type = ?"));
        assertTrue(sql.contains("md_parent_type = ?"));
        assertTrue(sql.contains("md_parent_code = ?"));
        assertTrue(sql.contains("status = 'A' AND is_active = TRUE"));
        assertTrue(sql.contains("ORDER BY md_desc, md_code"));
    }

    @Test
    void invalidHierarchyIsRejectedBeforeSaving() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                contains("VAULT_SHELF"), eq(Boolean.class),
                anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        VaultMaintenanceRepository repository = new VaultMaintenanceRepository(jdbcTemplate);

        assertThrows(IllegalArgumentException.class,
                () -> repository.createDraft(input(), "maker", "127.0.0.1"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(), eq(Boolean.class),
                eq("LOCATION-1"), eq("BRANCH-1"), eq("ROOM-1"), eq("RACK-1"), eq("SHELF-1"));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("branch.md_parent_code = location.md_code"));
        assertTrue(sql.contains("room.md_parent_code = branch.md_code"));
        assertTrue(sql.contains("rack.md_parent_code = room.md_code"));
        assertTrue(sql.contains("shelf.md_parent_code = rack.md_code"));
        verify(jdbcTemplate, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void searchIncludesMainAndWorkbenchRowsAndUsesStablePagingSql() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class), ArgumentMatchers.<Object[]>any()))
                .thenReturn(0L);
        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<VaultMaintenanceRecord>>any(),
                eq("P"), eq(25), eq(0L))).thenReturn(List.of());
        VaultMaintenanceRepository repository = new VaultMaintenanceRepository(jdbcTemplate);
        VaultMaintenanceSearch search = new VaultMaintenanceSearch(
                null, null, null, null, null, null, null, "P", null, null, 0, 25);

        repository.search(search, 0, 25);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                ArgumentMatchers.<RowMapper<VaultMaintenanceRecord>>any(),
                eq("P"), eq(25), eq(0L));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.startsWith("SELECT * FROM ("));
        assertTrue(sql.contains("UNION ALL"));
        assertTrue(sql.contains("FROM dms.vault_storage_mst"));
        assertTrue(sql.contains("FROM dms.vault_storage_wb"));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
    }

    @Test
    void saveDraftAcceptsIRejectedAndDeletedWorkbenchStatuses() {
        for (String status : List.of("I", "R", "D")) {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            VaultMaintenanceRecord before = record(status, "I", null, 1);
            VaultMaintenanceRecord after = record("I", "I", null, 1);
            stubWorkbenchReadsAndDropdownValidation(jdbcTemplate, before, after);
            stubDraftUpdate(jdbcTemplate);
            VaultMaintenanceRepository repository = new VaultMaintenanceRepository(jdbcTemplate);

            repository.updateDraft(before.wbId(), input(), "editor", "127.0.0.1");

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).update(
                    sqlCaptor.capture(),
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyBoolean(), anyString(), anyInt(), any(UUID.class));
            assertTrue(sqlCaptor.getValue().contains("status IN ('I', 'R', 'D')"));
            assertTrue(sqlCaptor.getValue().contains("status = 'I'"));
        }
    }

    @Test
    void submitAcceptsIRejectedAndDeletedWorkbenchStatuses() {
        for (String status : List.of("I", "R", "D")) {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            VaultMaintenanceRecord before = record(status, "I", null, 1);
            VaultMaintenanceRecord after = record("P", "I", "editor", 1);
            stubWorkbenchReadsAndDropdownValidation(jdbcTemplate, before, after);
            when(jdbcTemplate.update(
                    contains("SET status = 'P'"), anyString(), anyString(), any(UUID.class)))
                    .thenReturn(1);
            VaultMaintenanceRepository repository = new VaultMaintenanceRepository(jdbcTemplate);

            repository.submit(before.wbId(), "editor", "127.0.0.1");

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).update(
                    sqlCaptor.capture(), anyString(), anyString(), any(UUID.class));
            assertTrue(sqlCaptor.getValue().contains("status IN ('I', 'R', 'D')"));
            assertTrue(sqlCaptor.getValue().contains("status = 'P'"));
        }
    }

    @Test
    void staleEditApprovalFailsBeforeArchivingOrUpdatingMainRecord() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VaultMaintenanceRecord pending = record("P", "U", "maker", 3);
        VaultMaintenanceRecord current = record("A", null, "maker", 3);
        when(jdbcTemplate.queryForObject(
                contains("VAULT_SHELF"), eq(Boolean.class),
                anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<VaultMaintenanceRecord>>any(),
                ArgumentMatchers.<Object[]>any())).thenReturn(List.of(pending), List.of(current));
        VaultMaintenanceRepository repository = new VaultMaintenanceRepository(jdbcTemplate);

        assertThrows(IllegalStateException.class,
                () -> repository.approve(pending.wbId(), "reviewer", "127.0.0.1"));

        verify(jdbcTemplate, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void workbenchAuditRemainsRetrievableAfterApprovalRemovesWorkbenchRow() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UUID wbId = UUID.fromString("8ca2cc9c-9a7f-4de5-9a31-06e66d6e47d2");
        VaultMaintenanceAuditItem approved = new VaultMaintenanceAuditItem(
                1L, "A", "VAULT_STORAGE", "501", "reviewer", null,
                "Approved vault record", "Approved workbench request: WB:" + wbId);
        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<VaultMaintenanceAuditItem>>any(),
                eq("VAULT_STORAGE"),
                eq("WB:" + wbId),
                eq("Approved workbench request: WB:" + wbId))).thenReturn(List.of(approved));
        VaultMaintenanceRepository repository = new VaultMaintenanceRepository(jdbcTemplate);

        List<VaultMaintenanceAuditItem> items = repository.findWorkbenchAudit(wbId);

        assertTrue(items.contains(approved));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                ArgumentMatchers.<RowMapper<VaultMaintenanceAuditItem>>any(),
                eq("VAULT_STORAGE"),
                eq("WB:" + wbId),
                eq("Approved workbench request: WB:" + wbId));
        assertTrue(sqlCaptor.getValue().contains("action_description = 'Approved vault record'"));
    }

    private static void stubWorkbenchReadsAndDropdownValidation(
            JdbcTemplate jdbcTemplate, VaultMaintenanceRecord before, VaultMaintenanceRecord after) {
        when(jdbcTemplate.query(
                anyString(),
                ArgumentMatchers.<RowMapper<VaultMaintenanceRecord>>any(),
                ArgumentMatchers.<Object[]>any())).thenReturn(List.of(before), List.of(after));
        when(jdbcTemplate.queryForObject(
                contains("VAULT_SHELF"), eq(Boolean.class),
                anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jdbcTemplate.queryForObject(
                contains("SELECT EXISTS"), eq(Boolean.class), anyString())).thenReturn(false);
    }

    private static void stubDraftUpdate(JdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.update(
                contains("SET vault_tray_code = ?"),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyBoolean(), anyString(), anyInt(), any(UUID.class))).thenReturn(1);
    }

    private static VaultMaintenanceInput input() {
        return new VaultMaintenanceInput(
                "TRAY-1", "Tray one", "SHELF-1", "RACK-1", "ROOM-1",
                "BRANCH-1", "LOCATION-1", true);
    }

    private static VaultMaintenanceRecord record(
            String status, String action, String submitter, int version) {
        return new VaultMaintenanceRecord(
                51L,
                UUID.fromString("8ca2cc9c-9a7f-4de5-9a31-06e66d6e47d2"),
                "TRAY-1",
                "Tray one",
                "SHELF-1",
                "RACK-1",
                "ROOM-1",
                "BRANCH-1",
                "LOCATION-1",
                true,
                status,
                action,
                "maker",
                null,
                submitter,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                version);
    }
}
