package com.abgl.dms.documentupload;

import com.abgl.dms.i18n.ApplicationMessages;
import com.abgl.dms.i18n.MessageKeys;
import com.abgl.dms.repository.DocumentUploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);
    private static final Set<String> REFERENCE_TYPES = Set.of("Client", "ThirdParty", "Entity");
    private static final int MAX_DOCUMENTS_PER_REQUEST = 25;
    private static final int INITIAL_DOCUMENT_VERSION = 1;
    private static final DateTimeFormatter MONTH_DIRECTORY = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final DocumentUploadRepository documentUploadRepository;
    private final DocumentMetadataPersistenceService persistenceService;
    private final DocumentFileValidator fileValidator;
    private final ApplicationMessages messages;
    private final ClamAvScanner clamAvScanner;
    private final Path storagePath;
    private final Path quarantinePath;
    private final boolean clamAvEnabled;

    public DocumentUploadService(
            DocumentUploadRepository documentUploadRepository,
            DocumentMetadataPersistenceService persistenceService,
            DocumentFileValidator fileValidator,
            ApplicationMessages messages,
            ClamAvScanner clamAvScanner,
            @Value("${file.upload.storage-path}") String storagePath,
            @Value("${file.upload.quarantine-path}") String quarantinePath,
            @Value("${file.upload.clamav.enabled:false}") boolean clamAvEnabled) {
        this.documentUploadRepository = documentUploadRepository;
        this.persistenceService = persistenceService;
        this.fileValidator = fileValidator;
        this.messages = messages;
        this.clamAvScanner = clamAvScanner;
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
        this.quarantinePath = Path.of(quarantinePath).toAbsolutePath().normalize();
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
            Map<String, MultipartFile> namedFiles,
            String remoteAddress) {
        validateBatch(batch);
        validateFileParts(batch, orderedFiles, namedFiles);
        UploadUser user = documentUploadRepository.findUploadUser(username)
                .orElseThrow(() -> new DocumentUploadException(
                        "FORBIDDEN", messages.get(MessageKeys.AUTH_DMS_USER_NOT_FOUND)));

        List<DocumentUploadResult> results = new ArrayList<>(batch.documents().size());
        Set<String> usedFileParts = new HashSet<>();
        for (int index = 0; index < batch.documents().size(); index++) {
            DocumentUploadMetadata metadata = batch.documents().get(index);
            if (metadata == null) {
                results.add(rejected(null, MessageKeys.DOCUMENT_UPLOAD_METADATA_MISSING));
                continue;
            }
            MultipartFile file = resolveFile(metadata, index, orderedFiles, namedFiles, usedFileParts);
            if (file == null) {
                results.add(rejected(metadata, MessageKeys.DOCUMENT_UPLOAD_FILE_MISSING));
                continue;
            }
            results.add(uploadOne(username, user, metadata, file, remoteAddress));
        }
        return new DocumentUploadResponse(List.copyOf(results));
    }

    private DocumentUploadResult uploadOne(
            String username,
            UploadUser user,
            DocumentUploadMetadata metadata,
            MultipartFile file,
            String remoteAddress) {
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
                throw rejected(MessageKeys.DOCUMENT_UPLOAD_EXPIRY_BEFORE_DOCUMENT);
            }
            if (!documentUploadRepository.hasEntityVisibility(user.userCode(), metadata.referenceCode())) {
                throw rejected(MessageKeys.DOCUMENT_UPLOAD_REFERENCE_UNAUTHORIZED);
            }

            AuthorizedDocumentType documentType = documentUploadRepository.findAuthorizedDocumentType(
                            username, metadata.documentTypeCode(), metadata.categoryCode())
                    .orElseThrow(() -> new DocumentUploadException(
                            "VALIDATION FAILED", messages.get(MessageKeys.DOCUMENT_UPLOAD_TYPE_UNAUTHORIZED)));
            validateAgainstDocumentType(metadata, documentType);

            String extension = fileValidator.validateAndGetExtension(file);
            String actualFileName = actualFileName(file);
            long imageId = documentUploadRepository.nextImageId();
            String storedFileName = imageId + "_" + INITIAL_DOCUMENT_VERSION + "." + extension;
            LocalDate storageDate = LocalDate.now();
            String relativeDirectory = "/" + storageDate.getYear()
                    + "/" + storageDate.format(MONTH_DIRECTORY)
                    + "/" + String.format(Locale.ROOT, "%02d", storageDate.getDayOfMonth());
            Path relativePath = Path.of(
                    Integer.toString(storageDate.getYear()),
                    storageDate.format(MONTH_DIRECTORY),
                    String.format(Locale.ROOT, "%02d", storageDate.getDayOfMonth()));

            long internalDocumentId;
            if (clamAvEnabled) {
                Files.createDirectories(quarantinePath);
                quarantinedFile = quarantinePath.resolve(UUID.randomUUID() + ".quarantine");
                FileDetails fileDetails = copyAndHash(file.getInputStream(), quarantinedFile);
                fileValidator.validateSignature(quarantinedFile, extension);

                try {
                    clamAvScanner.scan(quarantinedFile);
                } catch (ClamAvScanner.InfectedFileException exception) {
                    throw new DocumentUploadException(
                            "REJECTED", messages.get(MessageKeys.DOCUMENT_UPLOAD_MALWARE_DETECTED), exception);
                } catch (IOException exception) {
                    throw new DocumentUploadException(
                            "FAILED", messages.get(MessageKeys.DOCUMENT_UPLOAD_SCANNER_UNAVAILABLE), exception);
                }
                Path targetDirectory = storagePath.resolve(relativePath).normalize();
                Files.createDirectories(targetDirectory);
                storedFile = targetDirectory.resolve(storedFileName).normalize();
                ensureWithinStorage(storedFile);
                moveApprovedFile(quarantinedFile, storedFile);
                quarantinedFile = null;
                internalDocumentId = persistDocument(username, metadata, documentType, documentDate, expiryDate,
                        imageId, INITIAL_DOCUMENT_VERSION, storedFileName, actualFileName, extension,
                        fileDetails, relativeDirectory, remoteAddress);
            } else {
                Path targetDirectory = storagePath.resolve(relativePath).normalize();
                Files.createDirectories(targetDirectory);
                storedFile = targetDirectory.resolve(storedFileName).normalize();
                ensureWithinStorage(storedFile);
                FileDetails fileDetails = copyAndHash(file.getInputStream(), storedFile);
                fileValidator.validateSignature(storedFile, extension);
                internalDocumentId = persistDocument(username, metadata, documentType, documentDate, expiryDate,
                        imageId, INITIAL_DOCUMENT_VERSION, storedFileName, actualFileName, extension,
                        fileDetails, relativeDirectory, remoteAddress);
            }

            persisted = true;
            log.info("Document upload completed; documentId={}, imageId={}, clientDocumentId={}, username={}",
                    internalDocumentId, imageId, metadata.clientDocumentId(), username);
            boolean autoApproved = "AUTO".equalsIgnoreCase(documentType.approvalType().trim());
            return new DocumentUploadResult(
                    metadata.clientDocumentId(),
                    imageId,
                    metadata.title().trim(),
                    autoApproved ? "APPROVED" : "PENDING_APPROVAL",
                    messages.get(autoApproved
                            ? MessageKeys.DOCUMENT_UPLOAD_SUCCESS_APPROVED
                            : MessageKeys.DOCUMENT_UPLOAD_SUCCESS_PENDING));
        } catch (DocumentUploadException exception) {
            log.warn("Document upload rejected or failed; clientDocumentId={}, username={}, status={}, reason={}",
                    metadata.clientDocumentId(), username, exception.status(), exception.publicMessage());
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, metadata.title(), exception.status(), exception.publicMessage());
        } catch (Exception exception) {
            log.error("Unexpected document upload failure; clientDocumentId={}, username={}",
                    metadata.clientDocumentId(), username, exception);
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, metadata.title(), "FAILED",
                    messages.get(MessageKeys.DOCUMENT_UPLOAD_FAILURE));
        } finally {
            deleteQuietly(quarantinedFile);
            if (!persisted) {
                deleteQuietly(storedFile);
            }
        }
    }

    private long persistDocument(
            String username,
            DocumentUploadMetadata metadata,
            AuthorizedDocumentType documentType,
            LocalDate documentDate,
            LocalDate expiryDate,
            long imageId,
            int version,
            String storedFileName,
            String actualFileName,
            String extension,
            FileDetails fileDetails,
            String relativeDirectory,
            String remoteAddress) {
        String approvalStatus = "AUTO".equalsIgnoreCase(documentType.approvalType().trim()) ? "A" : "P";
        return persistenceService.persist(new DocumentRecord(
                imageId,
                version,
                documentType.documentTypeCode(),
                metadata.referenceCode(),
                documentDate,
                expiryDate,
                metadata.title().trim(),
                metadata.additionalInformation(),
                approvalStatus,
                username,
                relativeDirectory,
                storedFileName,
                actualFileName,
                extension,
                fileDetails.size(),
                fileDetails.sha256(),
                remoteAddress
        ));
    }

    private String actualFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_FILENAME_REQUIRED);
        }
        String normalizedFileName = originalFileName.replace('\\', '/');
        return normalizedFileName.substring(normalizedFileName.lastIndexOf('/') + 1);
    }

    private FileDetails copyAndHash(InputStream input, Path destination) throws IOException {
        try (InputStream source = input;
             OutputStream output = Files.newOutputStream(
                     destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            MessageDigest digest = sha256Digest();
            byte[] buffer = new byte[8192];
            long size = 0;
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                digest.update(buffer, 0, bytesRead);
                size += bytesRead;
            }
            return new FileDetails(size, HexFormat.of().formatHex(digest.digest()));
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void ensureWithinStorage(Path file) {
        if (!file.startsWith(storagePath)) {
            throw new IllegalStateException("Generated storage path is outside the configured storage directory");
        }
    }

    private record FileDetails(long size, String sha256) {}

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
                        "REJECTED", messages.get(MessageKeys.DOCUMENT_UPLOAD_FILE_PARTS_MODE_INVALID));
            }
            return;
        }

        Set<String> requestedParts = new HashSet<>();
        for (DocumentUploadMetadata document : batch.documents()) {
            if (document == null || document.filePart() == null || document.filePart().isBlank()
                    || !requestedParts.add(document.filePart().trim())) {
                throw new DocumentUploadException(
                        "REJECTED", messages.get(MessageKeys.DOCUMENT_UPLOAD_FILE_PARTS_INVALID));
            }
        }
        if (!requestedParts.containsAll(namedFiles.keySet())) {
            throw new DocumentUploadException(
                    "REJECTED", messages.get(MessageKeys.DOCUMENT_UPLOAD_FILE_PARTS_UNREFERENCED));
        }
    }

    private void validateBatch(DocumentUploadBatchMetadata batch) {
        if (batch == null || batch.kind() == null || batch.kind().isBlank()
                || batch.documents() == null || batch.documents().isEmpty()
                || batch.documents().size() > MAX_DOCUMENTS_PER_REQUEST) {
            throw new DocumentUploadException(
                    "REJECTED",
                    messages.get(MessageKeys.DOCUMENT_UPLOAD_BATCH_INVALID, MAX_DOCUMENTS_PER_REQUEST));
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
                || metadata.documentTypeCode().length() > 50
                || metadata.referenceCode().length() > 100
                || isBlank(metadata.title())
                || metadata.title().length() > 250
                || isBlank(metadata.documentDate())
                || metadata.criticalIndicator() == null
                || isBlank(metadata.approvalType())) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_METADATA_FIELDS_INVALID);
        }
        if (metadata.branchCode() != null && metadata.branchCode().length() > 20) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_BRANCH_TOO_LONG);
        }
        if (metadata.additionalInformation() != null && metadata.additionalInformation().length() > 10000) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_ADDITIONAL_INFORMATION_TOO_LONG);
        }
    }

    private void validateAgainstDocumentType(
            DocumentUploadMetadata metadata, AuthorizedDocumentType documentType) {
        if (metadata.criticalIndicator() != documentType.criticalIndicator()
                || documentType.approvalType() == null
                || !metadata.approvalType().trim().equalsIgnoreCase(documentType.approvalType().trim())) {
            throw new DocumentUploadException(
                    "REJECTED", messages.get(MessageKeys.DOCUMENT_UPLOAD_METADATA_TYPE_MISMATCH));
        }
        if (documentType.expiryDateMandatory()
                && (metadata.expiryDate() == null || metadata.expiryDate().isBlank())) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_EXPIRY_REQUIRED);
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new DocumentUploadException(
                    "REJECTED",
                    messages.get(MessageKeys.DOCUMENT_UPLOAD_DATE_INVALID, fieldName), exception);
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

    private DocumentUploadResult rejected(DocumentUploadMetadata metadata, String messageKey) {
        return new DocumentUploadResult(
                metadata == null ? null : metadata.clientDocumentId(),
                null,
                metadata == null ? null : metadata.title(),
                "REJECTED",
                messages.get(messageKey));
    }

    private DocumentUploadException rejected(String messageKey) {
        return new DocumentUploadException("REJECTED", messages.get(messageKey));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
