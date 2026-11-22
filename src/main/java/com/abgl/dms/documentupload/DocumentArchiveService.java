package com.abgl.dms.documentupload;

import com.abgl.dms.i18n.ApplicationMessages;
import com.abgl.dms.i18n.MessageKeys;
import org.springframework.stereotype.Service;

@Service
public class DocumentArchiveService {

    private final DocumentMetadataPersistenceService persistenceService;
    private final ApplicationMessages messages;

    public DocumentArchiveService(
            DocumentMetadataPersistenceService persistenceService,
            ApplicationMessages messages) {
        this.persistenceService = persistenceService;
        this.messages = messages;
    }

    public String archive(String username, DocumentArchiveRequest request, String ipAddress) {
        try {
            persistenceService.archive(username, request.imageId(), request.version(), ipAddress);
        } catch (DocumentNotAccessibleException exception) {
            throw new IllegalArgumentException(
                    messages.get(MessageKeys.DOCUMENT_NOT_FOUND_OR_UNAUTHORIZED), exception);
        }
        return messages.get(MessageKeys.DOCUMENT_ARCHIVE_SUCCESS);
    }
}
