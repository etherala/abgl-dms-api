package com.abgl.dms.controller;

import com.abgl.dms.documentupload.DocumentUploadBatchMetadata;
import com.abgl.dms.documentupload.DocumentUploadException;
import com.abgl.dms.documentupload.DocumentUploadResponse;
import com.abgl.dms.documentupload.DocumentUploadService;
import com.abgl.dms.i18n.ApplicationMessages;
import com.abgl.dms.i18n.MessageKeys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadController.class);

    private final DocumentUploadService documentUploadService;
    private final ObjectMapper objectMapper;
    private final ApplicationMessages messages;

    public DocumentUploadController(
            DocumentUploadService documentUploadService,
            ObjectMapper objectMapper,
            ApplicationMessages messages) {
        this.documentUploadService = documentUploadService;
        this.objectMapper = objectMapper;
        this.messages = messages;
    }

    @PostMapping(consumes = "multipart/form-data")
    public DocumentUploadResponse upload(
            @RequestPart("metadata") String metadataJson,
            MultipartHttpServletRequest multipartRequest,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        DocumentUploadBatchMetadata metadata = parseMetadata(metadataJson);
        MultiValueMap<String, MultipartFile> multipartFiles = multipartRequest.getMultiFileMap();
        List<MultipartFile> orderedFiles = multipartFiles.getOrDefault("files", List.of());
        Map<String, MultipartFile> namedFiles = multipartRequest.getFileMap().entrySet().stream()
                .filter(entry -> !"metadata".equals(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        try {
            return documentUploadService.upload(
                    authentication.getName(), metadata, orderedFiles, namedFiles,
                    servletRequest.getRemoteAddr());
        } catch (DocumentUploadException exception) {
            HttpStatus status = "FORBIDDEN".equals(exception.status())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, exception.publicMessage());
        }
    }

    private DocumentUploadBatchMetadata parseMetadata(String metadataJson) {
        try {
            DocumentUploadBatchMetadata metadata =
                    objectMapper.readValue(metadataJson, DocumentUploadBatchMetadata.class);
            log.debug("Received document upload metadata: {}", metadata);
            if (metadata.documents() != null) {
                for (int index = 0; index < metadata.documents().size(); index++) {
                    var document = metadata.documents().get(index);
                    if (document != null) {
                        log.info("Document upload dates received; recordIndex={}, documentDate={}, expiryDate={}",
                                index, document.documentDate(), document.expiryDate());
                    }
                }
            }
            return metadata;
        } catch (JacksonException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, messages.get(MessageKeys.DOCUMENT_UPLOAD_METADATA_INVALID));
        }
    }
}
