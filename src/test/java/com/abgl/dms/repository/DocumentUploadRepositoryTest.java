package com.abgl.dms.repository;

import com.abgl.dms.documentupload.DocumentApprovalRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentUploadRepositoryTest {

    @Test
    void preventsMakerCheckerSubmitterFromReviewingTheirOwnDocumentBeforeWritingAudit() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("document_id")).thenReturn(27L);
        when(resultSet.getString("created_by")).thenReturn("maker");
        when(resultSet.getString("approval_type")).thenReturn("MAKER_CHECKER");
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        DocumentUploadRepository repository = new DocumentUploadRepository(jdbcTemplate);

        assertThrows(IllegalArgumentException.class,
                () -> repository.decideDocument(
                        "MAKER", new DocumentApprovalRecord(123L, 1, null), "A", "127.0.0.1"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
