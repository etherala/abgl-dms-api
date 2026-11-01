package com.abgl.dms.controller;

import com.abgl.dms.documenttype.DocumentType;
import com.abgl.dms.documenttype.DocumentTypeService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
@Validated
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    public DocumentTypeController(DocumentTypeService documentTypeService) {
        this.documentTypeService = documentTypeService;
    }

    @GetMapping
    public List<DocumentType> getActiveDocumentTypes(
            @RequestParam("categoryCode") @NotBlank @Size(max = 100) String categoryCode) {
        return documentTypeService.getActiveByCategory(categoryCode.trim());
    }
}
