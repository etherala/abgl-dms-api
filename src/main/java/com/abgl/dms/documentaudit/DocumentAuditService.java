package com.abgl.dms.documentaudit;

import com.abgl.dms.documentupload.DocumentNotAccessibleException;
import com.abgl.dms.repository.DocumentAuditRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentAuditService {

    private final DocumentAuditRepository documentAuditRepository;

    public DocumentAuditService(DocumentAuditRepository documentAuditRepository) {
        this.documentAuditRepository = documentAuditRepository;
    }

    public List<DocumentAuditItem> getAudit(long imageId, String workflowType, String username) {
        if (imageId < 1) {
            throw new IllegalArgumentException("Image ID must be positive");
        }
        if (!documentAuditRepository.isVisibleToUser(imageId, username)) {
            throw new DocumentNotAccessibleException();
        }
        return documentAuditRepository.findAudit(imageId);
    }
}
