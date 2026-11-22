package com.abgl.dms.controller;

import com.abgl.dms.documentupload.DocumentArchiveRequest;
import com.abgl.dms.documentupload.DocumentArchiveService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/document/archive")
@Validated
public class DocumentArchiveController {

    private final DocumentArchiveService documentArchiveService;

    public DocumentArchiveController(DocumentArchiveService documentArchiveService) {
        this.documentArchiveService = documentArchiveService;
    }

    @PostMapping
    public Map<String, String> archive(
            Authentication authentication,
            @Valid @RequestBody DocumentArchiveRequest request,
            HttpServletRequest servletRequest) {
        try {
            return Map.of("message", documentArchiveService.archive(
                    authentication.getName(), request, servletRequest.getRemoteAddr()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }
}
