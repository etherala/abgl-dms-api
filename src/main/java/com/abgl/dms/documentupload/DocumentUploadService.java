package com.abgl.dms.documentupload;

import com.abgl.dms.repository.DocumentUploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);
    private static final Set<String> REFERENCE_TYPES = Set.of("Client", "ThirdParty", "Entity");
    private static final int MAX_DOCUMENTS_PER_REQUEST = 25;

    private final DocumentUploadRepository documentUploadRepository;
    private final DocumentMetadataPersistenceService persistenceService;
    private final DocumentFileValidator fileValidator;
    private final ClamAvScanner clamAvScanner;
    private final Path storagePath;
    private final Path quarantinePath;
    private final long documentCollectionId;
    private final long folderId;
    private final boolean clamAvEnabled;

    public DocumentUploadService(
            DocumentUploadRepository documentUploadRepository,
            DocumentMetadataPersistenceService persistenceService,
            DocumentFileValidator fileValidator,
            ClamAvScanner clamAvScanner,
            @Value("${file.upload.storage-path}") String storagePath,
            @Value("${file.upload.quarantine-path}") String quarantinePath,
            @Value("${file.upload.document-collection-id:4}") long documentCollectionId,
            @Value("${file.upload.folder-id:23}") long folderId,
            @Value("${file.upload.clamav.enabled:false}") boolean clamAvEnabled) {
        this.documentUploadRepository = documentUploadRepository;
        this.persistenceService = persistenceService;
        this.fileValidator = fileValidator;
        this.clamAvScanner = clamAvScanner;
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
        this.quarantinePath = Path.of(quarantinePath).toAbsolutePath().normalize();
        if (documentCollectionId < 1 || folderId < 1) {
            throw new IllegalArgumentException("Upload collection and folder IDs must be positive");
        }
        this.documentCollectionId = documentCollectionId;
        this.folderId = folderId;
        this.clamAvEnabled = clamAvEnabled;
        if (this.storagePath.equals(this.quarantinePath)
                || this.storagePath.startsWith(this.quarantinePath)
                || this.quarantinePath.startsWith(this.storagePath)) {
            throw new IllegalArgumentException("Storage and quarantine paths must be separate directories");
        }
    }

    public DocumentUploadResponse upload(
            String username,
            DocumentUploadBatchMetadata batch,
            List<MultipartFile> orderedFiles,
            Map<String, MultipartFile> namedFiles) {
        validateBatch(batch);
        validateFileParts(batch, orderedFiles, namedFiles);
        UploadUser user = documentUploadRepository.findUploadUser(username)
                .orElseThrow(() -> new DocumentUploadException("FORBIDDEN", "Authenticated DMS user was not found"));

        List<DocumentUploadResult> results = new ArrayList<>(batch.documents().size());
        Set<String> usedFileParts = new HashSet<>();
        for (int index = 0; index < batch.documents().size(); index++) {
            DocumentUploadMetadata metadata = batch.documents().get(index);
            if (metadata == null) {
                results.add(rejected(null, "Document metadata is missing"));
                continue;
            }
            MultipartFile file = resolveFile(metadata, index, orderedFiles, namedFiles, usedFileParts);
            if (file == null) {
                results.add(rejected(metadata, "Required file part was not provided"));
                continue;
            }
            results.add(uploadOne(username, user, metadata, file));
        }
        return new DocumentUploadResponse(List.copyOf(results));
    }

    private DocumentUploadResult uploadOne(
            String username, UploadUser user, DocumentUploadMetadata metadata, MultipartFile file) {
        Path quarantinedFile = null;
        Path storedFile = null;
        boolean persisted = false;
        try {
            validateMetadata(metadata);
            LocalDate documentDate = parseDate(metadata.documentDate(), "documentDate");
            LocalDate expiryDate = metadata.expiryDate() == null || metadata.expiryDate().isBlank()
                    ? null
                    : parseDate(metadata.expiryDate(), "expiryDate");
            if (expiryDate != null && expiryDate.isBefore(documentDate)) {
                throw new DocumentUploadException("REJECTED", "Expiry date cannot be before document date");
            }
            if (!documentUploadRepository.hasEntityVisibility(user.userCode(), metadata.referenceCode())) {
                throw new DocumentUploadException("REJECTED", "User is not authorized for this reference");
            }

            AuthorizedDocumentType documentType = documentUploadRepository.findAuthorizedDocumentType(
                            username, metadata.documentTypeCode(), metadata.categoryCode(), documentCollectionId)
                    .orElseThrow(() -> new DocumentUploadException(
                            "REJECTED", "User is not authorized for this active document type and collection"));
            validateAgainstDocumentType(metadata, documentType);

            String extension = fileValidator.validateAndGetExtension(file);
            if (clamAvEnabled) {
                Files.createDirectories(quarantinePath);
                quarantinedFile = quarantinePath.resolve(UUID.randomUUID() + ".quarantine");
                try (var input = file.getInputStream()) {
                    Files.copy(input, quarantinedFile);
                }
                fileValidator.validateSignature(quarantinedFile, extension);

                try {
                    clamAvScanner.scan(quarantinedFile);
                } catch (ClamAvScanner.InfectedFileException exception) {
                    throw new DocumentUploadException(
                            "REJECTED", "Malware was detected in the uploaded file", exception);
                } catch (IOException exception) {
                    throw new DocumentUploadException(
                            "FAILED", "Malware scanning is unavailable; file was not accepted", exception);
                }
            }

            Files.createDirectories(storagePath);
            String imageId = documentUploadRepository.nextImageId();
            storedFile = storagePath.resolve(imageId + "." + extension).normalize();
            if (!storedFile.startsWith(storagePath)) {
                throw new IllegalStateException("Generated storage path is outside the configured storage directory");
            }
            if (clamAvEnabled) {
                moveApprovedFile(quarantinedFile, storedFile);
                quarantinedFile = null;
            } else {
                try (var input = file.getInputStream()) {
                    Files.copy(input, storedFile);
                }
                fileValidator.validateSignature(storedFile, extension);
            }

            long documentId = persistenceService.persist(new DocumentRecord(
                    imageId,
                    documentCollectionId,
                    documentType.documentTypeId(),
                    folderId,
                    metadata.referenceType(),
                    metadata.referenceCode(),
                    documentDate,
                    expiryDate,
                    metadata.title().trim(),
                    metadata.additionalInformation(),
                    user.userId(),
                    "Entity".equals(metadata.referenceType()) ? metadata.referenceCode() : null
            ));
            persisted = true;

            log.info("Document upload completed; documentId={}, clientDocumentId={}, username={}",
                    documentId, metadata.clientDocumentId(), username);
            return new DocumentUploadResult(metadata.clientDocumentId(), documentId, "UPLOADED", "Upload successful");
        } catch (DocumentUploadException exception) {
            log.warn("Document upload rejected or failed; clientDocumentId={}, username={}, status={}, reason={}",
                    metadata.clientDocumentId(), username, exception.status(), exception.publicMessage());
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, exception.status(), exception.publicMessage());
        } catch (Exception exception) {
            log.error("Unexpected document upload failure; clientDocumentId={}, username={}",
                    metadata.clientDocumentId(), username, exception);
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, "FAILED", "Document could not be uploaded");
        } finally {
            deleteQuietly(quarantinedFile);
            if (!persisted) {
                deleteQuietly(storedFile);
            }
        }
    }

    private MultipartFile resolveFile(
            DocumentUploadMetadata metadata,
            int index,
            List<MultipartFile> orderedFiles,
            Map<String, MultipartFile> namedFiles,
            Set<String> usedFileParts) {
        if (metadata.filePart() != null && !metadata.filePart().isBlank()) {
            String filePart = metadata.filePart().trim();
            if (!usedFileParts.add(filePart)) {
                return null;
            }
            return namedFiles.get(filePart);
        }
        if (index >= orderedFiles.size()) {
            return null;
        }
        return orderedFiles.get(index);
    }

    private void validateFileParts(
            DocumentUploadBatchMetadata batch,
            List<MultipartFile> orderedFiles,
            Map<String, MultipartFile> namedFiles) {
        boolean orderedMode = !orderedFiles.isEmpty()
                || batch.documents().stream().anyMatch(document ->
                document == null || document.filePart() == null || document.filePart().isBlank());
        if (orderedMode) {
            if (orderedFiles.size() > batch.documents().size()
                    || batch.documents().stream().anyMatch(document ->
                    document != null && document.filePart() != null && !document.filePart().isBlank())
                    || namedFiles.keySet().stream().anyMatch(partName -> !"files".equals(partName))) {
                throw new DocumentUploadException(
                        "REJECTED", "Provide exactly one ordered file per document or use named file parts");
            }
            return;
        }

        Set<String> requestedParts = new HashSet<>();
        for (DocumentUploadMetadata document : batch.documents()) {
            if (document == null || document.filePart() == null || document.filePart().isBlank()
                    || !requestedParts.add(document.filePart().trim())) {
                throw new DocumentUploadException(
                        "REJECTED", "Each document must reference a unique named file part");
            }
        }
        if (!requestedParts.containsAll(namedFiles.keySet())) {
            throw new DocumentUploadException(
                    "REJECTED", "Every named file part must be referenced in the metadata");
        }
    }

    private void validateBatch(DocumentUploadBatchMetadata batch) {
        if (batch == null || batch.kind() == null || batch.kind().isBlank()
                || batch.documents() == null || batch.documents().isEmpty()
                || batch.documents().size() > MAX_DOCUMENTS_PER_REQUEST) {
            throw new DocumentUploadException(
                    "REJECTED", "Metadata must contain a kind and between 1 and 25 documents");
        }
    }

    private void validateMetadata(DocumentUploadMetadata metadata) {
        if (metadata == null
                || isBlank(metadata.referenceCode())
                || isBlank(metadata.referenceType())
                || !REFERENCE_TYPES.contains(metadata.referenceType())
                || isBlank(metadata.categoryCode())
                || metadata.categoryCode().length() > 100
                || isBlank(metadata.documentTypeCode())
                || metadata.documentTypeCode().length() > 100
                || metadata.referenceCode().length() > 100
                || isBlank(metadata.title())
                || metadata.title().length() > 250
                || isBlank(metadata.documentDate())
                || metadata.criticalIndicator() == null
                || isBlank(metadata.approvalType())) {
            throw new DocumentUploadException("REJECTED", "Required document metadata is invalid");
        }
        if (metadata.branchCode() != null && metadata.branchCode().length() > 20) {
            throw new DocumentUploadException("REJECTED", "Branch code exceeds the allowed length");
        }
        if (metadata.additionalInformation() != null && metadata.additionalInformation().length() > 10000) {
            throw new DocumentUploadException("REJECTED", "Additional information exceeds the allowed length");
        }
    }

    private void validateAgainstDocumentType(
            DocumentUploadMetadata metadata, AuthorizedDocumentType documentType) {
        if (metadata.criticalIndicator() != documentType.criticalIndicator()
                || !metadata.approvalType().equals(documentType.approvalType())) {
            throw new DocumentUploadException(
                    "REJECTED", "Document metadata does not match the configured document type");
        }
        if (documentType.expiryDateMandatory()
                && (metadata.expiryDate() == null || metadata.expiryDate().isBlank())) {
            throw new DocumentUploadException("REJECTED", "An expiry date is required for this document type");
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new DocumentUploadException(
                    "REJECTED", fieldName + " must use ISO date format yyyy-MM-dd", exception);
        }
    }

    private void moveApprovedFile(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.error("Could not remove temporary or orphaned upload file", exception);
        }
    }

    private DocumentUploadResult rejected(DocumentUploadMetadata metadata, String message) {
        return new DocumentUploadResult(metadata == null ? null : metadata.clientDocumentId(), null, "REJECTED", message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
