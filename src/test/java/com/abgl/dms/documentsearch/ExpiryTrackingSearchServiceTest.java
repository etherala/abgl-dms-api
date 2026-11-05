package com.abgl.dms.documentsearch;

import com.abgl.dms.repository.DocumentSearchRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExpiryTrackingSearchServiceTest {

    private final DocumentSearchRepository repository = mock(DocumentSearchRepository.class);
    private final DocumentSearchService documentSearchService = new DocumentSearchService(repository);
    private final ExpiryTrackingSearchService service =
            new ExpiryTrackingSearchService(documentSearchService);

    @Test
    void mapsExpiryTrackingFiltersToSharedDocumentSearch() {
        ExpiryTrackingSearchRequest request = new ExpiryTrackingSearchRequest(
                "REF-1", "CAT-1", "TYPE-1", "agreement",
                null, null, null, null, null, null, "A",
                2, 5, 30, "DS");
        DocumentSearchRequest sharedRequest = new DocumentSearchRequest(
                "REF-1", "CAT-1", "TYPE-1", "agreement",
                null, null, null, null, null, null, "A",
                2, 5, 30, "DS");
        when(repository.countVisibleDocuments("admin", sharedRequest)).thenReturn(1L);
        when(repository.searchVisibleDocuments("admin", sharedRequest, 5, 5)).thenReturn(List.of());

        DocumentSearchPage page = service.search("admin", request);

        assertEquals(1, page.totalElements());
        assertEquals(2, page.page());
        verify(repository).countVisibleDocuments("admin", sharedRequest);
        verify(repository).searchVisibleDocuments("admin", sharedRequest, 5, 5);
    }

    @Test
    void rejectsNegativeMaximumDaysRemaining() {
        ExpiryTrackingSearchRequest request = new ExpiryTrackingSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, -1, null);

        assertThrows(IllegalArgumentException.class, () -> service.search("admin", request));
        verifyNoInteractions(repository);
    }

    @Test
    void normalizesSupportedExpiryStatus() {
        ExpiryTrackingSearchRequest request = new ExpiryTrackingSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null,
                1, 10, null, " ne ");
        DocumentSearchRequest sharedRequest = new DocumentSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null,
                1, 10, null, "NE");
        when(repository.countVisibleDocuments("admin", sharedRequest)).thenReturn(0L);
        when(repository.searchVisibleDocuments("admin", sharedRequest, 10, 0)).thenReturn(List.of());

        service.search("admin", request);

        verify(repository).countVisibleDocuments("admin", sharedRequest);
    }

    @Test
    void rejectsUnsupportedExpiryStatus() {
        ExpiryTrackingSearchRequest request = new ExpiryTrackingSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "UNKNOWN");

        assertThrows(IllegalArgumentException.class, () -> service.search("admin", request));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.search("admin", null));
        verifyNoInteractions(repository);
    }
}
