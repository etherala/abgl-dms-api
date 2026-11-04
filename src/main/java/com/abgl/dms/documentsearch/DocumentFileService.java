package com.abgl.dms.documentsearch;

import com.abgl.dms.repository.DocumentSearchRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DocumentFileService {

    private final DocumentSearchRepository repository;
    private final Path storageRoot;

    public DocumentFileService(
            DocumentSearchRepository repository,
            @Value("${file.upload.storage-path}") String storagePath) {
        this.repository = repository;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public DocumentFileResponse getDocument(
            String username, long imageId, int version) {
        if (imageId < 1 || version < 1) {
            throw new IllegalArgumentException("Image ID and version must be positive");
        }

        StoredDocument document = repository.findVisibleDocumentFile(username, imageId, version)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Document not found"));
        Path file = resolveStoredFile(document);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ResponseStatusException(NOT_FOUND, "Document file not found");
        }

        return new DocumentFileResponse(
                new FileSystemResource(file),
                safeDownloadFileName(document),
                mediaType(document.extension()));
    }

    private Path resolveStoredFile(StoredDocument document) {
        if (document.relativePath() == null || document.storedFileName() == null
                || document.storedFileName().isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, "Document file not found");
        }

        String relativePathValue = document.relativePath().replace('\\', '/');
        while (relativePathValue.startsWith("/")) {
            relativePathValue = relativePathValue.substring(1);
        }
        if (relativePathValue.isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, "Document file not found");
        }
        Path relativeDirectory = Path.of(relativePathValue).normalize();
        Path fileName = Path.of(document.storedFileName());
        if (relativeDirectory.isAbsolute() || relativeDirectory.startsWith("..")
                || fileName.isAbsolute() || fileName.getNameCount() != 1
                || fileName.startsWith("..")) {
            throw new ResponseStatusException(NOT_FOUND, "Document file not found");
        }

        Path resolved = storageRoot.resolve(relativeDirectory).resolve(fileName).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new ResponseStatusException(NOT_FOUND, "Document file not found");
        }
        try {
            Path realRoot = storageRoot.toRealPath();
            Path realFile = resolved.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                throw new ResponseStatusException(NOT_FOUND, "Document file not found");
            }
            return realFile;
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(NOT_FOUND, "Document file not found");
        }
    }

    private String safeDownloadFileName(StoredDocument document) {
        String fileName = document.actualFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = document.storedFileName();
        }
        String normalized = fileName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private MediaType mediaType(String extension) {
        if (extension == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "txt" -> MediaType.TEXT_PLAIN;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xls" -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "ppt" -> MediaType.parseMediaType("application/vnd.ms-powerpoint");
            case "pptx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
