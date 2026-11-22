package com.abgl.dms.controller;

import com.abgl.dms.documentaudit.DocumentAuditItem;
import com.abgl.dms.documentaudit.DocumentAuditRequest;
import com.abgl.dms.documentaudit.DocumentAuditService;
import com.abgl.dms.documentupload.DocumentNotAccessibleException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentAuditController {

    private final DocumentAuditService documentAuditService;

    public DocumentAuditController(DocumentAuditService documentAuditService) {
        this.documentAuditService = documentAuditService;
    }

    @PostMapping("/auditview")
    public List<DocumentAuditItem> getAudit(
            Authentication authentication,
            @Valid @RequestBody DocumentAuditRequest request) {
        try {
            return documentAuditService.getAudit(
                    request.imageId(), request.wflowType(), authentication.getName());
        } catch (DocumentNotAccessibleException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document was not found or the user is not authorized", exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
