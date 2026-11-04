package com.abgl.dms.controller;

import com.abgl.dms.documentsearch.DocumentSearchPage;
import com.abgl.dms.documentsearch.DocumentSearchRequest;
import com.abgl.dms.documentsearch.DocumentSearchService;
import com.abgl.dms.documentsearch.ApprovalDocumentSearchPage;
import com.abgl.dms.documentsearch.ApprovalDocumentSearchRequest;
import com.abgl.dms.documentsearch.ApprovalDocumentSearchService;
import com.abgl.dms.documentsearch.DocumentFileResponse;
import com.abgl.dms.documentsearch.DocumentFileService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
@RequestMapping("/api/documents")
public class DocumentSearchController {

    private final DocumentSearchService documentSearchService;
    private final ApprovalDocumentSearchService approvalDocumentSearchService;
    private final DocumentFileService documentFileService;

    public DocumentSearchController(
            DocumentSearchService documentSearchService,
            ApprovalDocumentSearchService approvalDocumentSearchService,
            DocumentFileService documentFileService) {
        this.documentSearchService = documentSearchService;
        this.approvalDocumentSearchService = approvalDocumentSearchService;
        this.documentFileService = documentFileService;
    }

    @PostMapping("/search")
    public DocumentSearchPage search(
            Authentication authentication,
            @RequestBody DocumentSearchRequest request) {
        try {
            return documentSearchService.search(authentication.getName(), request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/approval/search")
    public ApprovalDocumentSearchPage searchForApproval(
            Authentication authentication,
            @RequestBody ApprovalDocumentSearchRequest request) {
        try {
            return approvalDocumentSearchService.search(authentication.getName(), request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/view")
    public ResponseEntity<Resource> viewDocument(
            Authentication authentication,
            @RequestParam long imageId,
            @RequestParam int version,
            @RequestParam(defaultValue = "inline") String disposition) {
        String normalizedDisposition = disposition.trim().toLowerCase(Locale.ROOT);
        if (!"inline".equals(normalizedDisposition) && !"attachment".equals(normalizedDisposition)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Disposition must be inline or attachment");
        }

        DocumentFileResponse document = documentFileService.getDocument(
                authentication.getName(), imageId, version);
        ContentDisposition contentDisposition = ContentDisposition
                .builder(normalizedDisposition)
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(document.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(document.resource());
    }
}
