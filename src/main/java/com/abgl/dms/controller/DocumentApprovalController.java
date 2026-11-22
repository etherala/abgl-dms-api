package com.abgl.dms.controller;

import com.abgl.dms.documentapproval.DocumentApprovalRequest;
import com.abgl.dms.documentapproval.DocumentApprovalResponse;
import com.abgl.dms.documentapproval.DocumentApprovalService;
import com.abgl.dms.documentupload.DocumentNotAccessibleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents/approval")
@Validated
public class DocumentApprovalController {

    private final DocumentApprovalService documentApprovalService;

    public DocumentApprovalController(DocumentApprovalService documentApprovalService) {
        this.documentApprovalService = documentApprovalService;
    }

    @PostMapping
    public DocumentApprovalResponse decide(
            Authentication authentication,
            @Valid @RequestBody DocumentApprovalRequest request,
            HttpServletRequest servletRequest) {
        try {
            return documentApprovalService.decide(
                    authentication.getName(), request, servletRequest.getRemoteAddr());
        } catch (DocumentNotAccessibleException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
