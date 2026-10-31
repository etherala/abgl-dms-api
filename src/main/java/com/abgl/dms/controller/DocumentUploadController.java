package com.abgl.dms.controller;

import com.abgl.dms.documentupload.DocumentUploadBatchMetadata;
import com.abgl.dms.documentupload.DocumentUploadException;
import com.abgl.dms.documentupload.DocumentUploadResponse;
import com.abgl.dms.documentupload.DocumentUploadService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private final DocumentUploadService documentUploadService;
    private final ObjectMapper objectMapper;

    public DocumentUploadController(
            DocumentUploadService documentUploadService, ObjectMapper objectMapper) {
        this.documentUploadService = documentUploadService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = "multipart/form-data")
    public DocumentUploadResponse upload(
            @RequestPart("metadata") String metadataJson,
            MultipartHttpServletRequest multipartRequest,
            Authentication authentication) {
        DocumentUploadBatchMetadata metadata = parseMetadata(metadataJson);
        MultiValueMap<String, MultipartFile> multipartFiles = multipartRequest.getMultiFileMap();
        List<MultipartFile> orderedFiles = multipartFiles.getOrDefault("files", List.of());
        Map<String, MultipartFile> namedFiles = multipartRequest.getFileMap();
        try {
            return documentUploadService.upload(
                    authentication.getName(), metadata, orderedFiles, namedFiles);
        } catch (DocumentUploadException exception) {
            HttpStatus status = "FORBIDDEN".equals(exception.status())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, exception.publicMessage());
        }
    }

    private DocumentUploadBatchMetadata parseMetadata(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, DocumentUploadBatchMetadata.class);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload metadata is not valid JSON");
        }
    }
}
