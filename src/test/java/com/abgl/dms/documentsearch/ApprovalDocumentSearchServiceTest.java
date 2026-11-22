package com.abgl.dms.documentsearch;

import com.abgl.dms.repository.DocumentSearchRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalDocumentSearchServiceTest {

    private final DocumentSearchRepository repository = mock(DocumentSearchRepository.class);
    private final ApprovalDocumentSearchService service = new ApprovalDocumentSearchService(repository);

    @Test
    void appliesDefaultPaginationToApprovalSearch() {
        ApprovalDocumentSearchRequest request = request(null, null);
        when(repository.countVisibleApprovalDocuments("admin", request)).thenReturn(11L);
        when(repository.searchVisibleApprovalDocuments("admin", request, 10, 0)).thenReturn(List.of());

        ApprovalDocumentSearchPage page = service.search("admin", request);

        assertEquals(1, page.page());
        assertEquals(10, page.pageSize());
        assertEquals(11, page.totalElements());
        assertEquals(2, page.totalPages());
        verify(repository).searchVisibleApprovalDocuments("admin", request, 10, 0);
    }

    @Test
    void rejectsReversedApprovalDateRanges() {
        ApprovalDocumentSearchRequest request = new ApprovalDocumentSearchRequest(
                null, null, null, null, null, null,
                LocalDate.parse("2025-08-31"), LocalDate.parse("2025-08-01"),
                null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.search("admin", request));
    }

    private ApprovalDocumentSearchRequest request(Integer page, Integer pageSize) {
        return new ApprovalDocumentSearchRequest(
                null, null, null, null, null, "P",
                null, null, null, null, null, null,
                page, pageSize);
    }
}
