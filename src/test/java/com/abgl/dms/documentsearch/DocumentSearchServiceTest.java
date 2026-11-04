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

class DocumentSearchServiceTest {

    private final DocumentSearchRepository repository = mock(DocumentSearchRepository.class);
    private final DocumentSearchService service = new DocumentSearchService(repository);

    @Test
    void appliesDefaultPaginationWhenNotProvided() {
        DocumentSearchRequest request = new DocumentSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(repository.countVisibleDocuments("admin", request)).thenReturn(21L);
        when(repository.searchVisibleDocuments("admin", request, 10, 0)).thenReturn(List.of());

        DocumentSearchPage page = service.search("admin", request);

        assertEquals(1, page.page());
        assertEquals(10, page.pageSize());
        assertEquals(21, page.totalElements());
        assertEquals(3, page.totalPages());
        verify(repository).searchVisibleDocuments("admin", request, 10, 0);
    }

    @Test
    void calculatesOffsetForOneBasedPageNumber() {
        DocumentSearchRequest request = new DocumentSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null, 3, 5);
        when(repository.countVisibleDocuments("admin", request)).thenReturn(12L);
        when(repository.searchVisibleDocuments("admin", request, 5, 10)).thenReturn(List.of());

        DocumentSearchPage page = service.search("admin", request);

        assertEquals(3, page.page());
        assertEquals(5, page.pageSize());
        verify(repository).searchVisibleDocuments("admin", request, 5, 10);
    }

    @Test
    void rejectsReversedDateRanges() {
        DocumentSearchRequest request = new DocumentSearchRequest(
                null, null, null, null,
                LocalDate.parse("2025-08-31"), LocalDate.parse("2025-08-01"),
                null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.search("admin", request));
    }

    @Test
    void rejectsPageSizesAboveMaximum() {
        DocumentSearchRequest request = new DocumentSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null, 1, 101);

        assertThrows(IllegalArgumentException.class, () -> service.search("admin", request));
    }
}
