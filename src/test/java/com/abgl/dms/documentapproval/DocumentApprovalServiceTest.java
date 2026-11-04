package com.abgl.dms.documentapproval;

import com.abgl.dms.documentupload.DocumentApprovalRecord;
import com.abgl.dms.documentupload.DocumentMetadataPersistenceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentApprovalServiceTest {

    private final DocumentMetadataPersistenceService persistenceService =
            mock(DocumentMetadataPersistenceService.class);
    private final DocumentApprovalService service = new DocumentApprovalService(persistenceService);

    @Test
    void approvesMultipleDocumentsInOneOperation() {
        DocumentApprovalRequest request = new DocumentApprovalRequest(
                "approved",
                List.of(
                        new DocumentApprovalItem(12345L, 2, null),
                        new DocumentApprovalItem(12346L, 1, "Verified")));

        DocumentApprovalResponse response = service.decide("reviewer", request, "127.0.0.1");

        assertEquals(2, response.results().size());
        assertEquals("A", response.results().get(0).approvalStatus());
        verify(persistenceService).decideDocuments(
                "reviewer",
                List.of(
                        new DocumentApprovalRecord(12345L, 2, null),
                        new DocumentApprovalRecord(12346L, 1, "Verified")),
                "A",
                "127.0.0.1");
    }

    @Test
    void rejectsDocumentsAndPreservesRemarks() {
        DocumentApprovalRequest request = new DocumentApprovalRequest(
                "rejected",
                List.of(new DocumentApprovalItem(12345L, 2, "Missing signature.")));

        DocumentApprovalResponse response = service.decide("reviewer", request, "127.0.0.1");

        assertEquals("R", response.results().get(0).approvalStatus());
        verify(persistenceService).decideDocuments(
                "reviewer",
                List.of(new DocumentApprovalRecord(12345L, 2, "Missing signature.")),
                "R",
                "127.0.0.1");
    }

    @Test
    void rejectsDuplicateDocumentsBeforePersistence() {
        DocumentApprovalRequest request = new DocumentApprovalRequest(
                "approved",
                List.of(
                        new DocumentApprovalItem(12345L, 2, null),
                        new DocumentApprovalItem(12345L, 2, null)));

        assertThrows(IllegalArgumentException.class,
                () -> service.decide("reviewer", request, "127.0.0.1"));
    }
}
