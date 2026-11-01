package com.abgl.dms.documentupload;

import com.abgl.dms.repository.DocumentUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentMetadataPersistenceService {

    private final DocumentUploadRepository documentUploadRepository;

    public DocumentMetadataPersistenceService(DocumentUploadRepository documentUploadRepository) {
        this.documentUploadRepository = documentUploadRepository;
    }

    @Transactional
    public long persist(DocumentRecord document) {
        return documentUploadRepository.insertDocument(document);
    }
}
