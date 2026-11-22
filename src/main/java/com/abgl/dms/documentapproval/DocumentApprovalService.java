package com.abgl.dms.documentapproval;

import com.abgl.dms.documentupload.DocumentApprovalRecord;
import com.abgl.dms.documentupload.DocumentMetadataPersistenceService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentApprovalService {

    private final DocumentMetadataPersistenceService persistenceService;

    public DocumentApprovalService(DocumentMetadataPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public DocumentApprovalResponse decide(
            String username, DocumentApprovalRequest request, String ipAddress) {
        if (request == null || request.documents() == null || request.documents().isEmpty()) {
            throw new IllegalArgumentException("At least one document is required");
        }

        String action = request.action() == null
                ? ""
                : request.action().trim().toLowerCase(Locale.ROOT);
        String approvalStatus = switch (action) {
            case "approved" -> "A";
            case "rejected" -> "R";
            default -> throw new IllegalArgumentException(
                    "Action must be either approved or rejected");
        };

        Set<String> documentKeys = new HashSet<>();
        List<DocumentApprovalRecord> documents = request.documents().stream()
                .map(item -> {
                    if (item == null || item.imageId() == null || item.imageId() <= 0
                            || item.version() == null || item.version() <= 0) {
                        throw new IllegalArgumentException(
                                "Each document must have a positive imageId and version");
                    }
                    String key = item.imageId() + ":" + item.version();
                    if (!documentKeys.add(key)) {
                        throw new IllegalArgumentException(
                                "A document can only appear once in an approval request");
                    }
                    return new DocumentApprovalRecord(item.imageId(), item.version(), item.remarks());
                })
                .toList();

        persistenceService.decideDocuments(username, documents, approvalStatus, ipAddress);
        String message = "A".equals(approvalStatus) ? "Document approved." : "Document rejected.";
        return new DocumentApprovalResponse(documents.stream()
                .map(document -> new DocumentApprovalResult(
                        document.imageId(), document.version(), approvalStatus, message))
                .toList());
    }
}
