package com.abgl.dms.documenttype;

import com.abgl.dms.repository.DocumentTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;

    public DocumentTypeService(DocumentTypeRepository documentTypeRepository) {
        this.documentTypeRepository = documentTypeRepository;
    }

    public List<DocumentType> getActiveByCategory(String categoryCode) {
        return documentTypeRepository.findActiveByCategory(categoryCode);
    }
}
