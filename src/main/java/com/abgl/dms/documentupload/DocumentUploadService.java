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
        UploadUser user = documentUploadRepository.findUploadUser(username)
                .orElseThrow(() -> new DocumentUploadException(
                        "FORBIDDEN", messages.get(MessageKeys.AUTH_DMS_USER_NOT_FOUND)));

        String kind = batch.kind().trim().toLowerCase(Locale.ROOT);
        boolean usesLegacyMaintenance = ("edit".equals(kind) || "reindex".equals(kind))
                && batch.documents().stream().allMatch(document ->
                document == null || "SUBMIT".equals(actionOf(document, batch)));
        if (usesLegacyMaintenance) {
            validateNoFiles(batch, orderedFiles, namedFiles);
            return performMetadataUpdates(username, user, batch, kind, remoteAddress);
        }
        validateActionFileParts(batch, orderedFiles, namedFiles);

        List<DocumentUploadResult> results = new ArrayList<>(batch.documents().size());
        int orderedFileIndex = 0;
        Set<String> usedFileParts = new HashSet<>();
        for (DocumentUploadMetadata metadata : batch.documents()) {
            if (metadata == null) {
                results.add(rejected(null, MessageKeys.DOCUMENT_UPLOAD_METADATA_MISSING));
                continue;
            }
            String action = actionOf(metadata, batch);
            MultipartFile file = null;
            if (requiresFile(action)) {
                file = resolveActionFile(metadata, orderedFiles, namedFiles, usedFileParts, orderedFileIndex++);
                if (file == null) {
                    results.add(rejected(metadata, MessageKeys.DOCUMENT_UPLOAD_FILE_MISSING));
                    continue;
                }
            }
            results.add(switch (action) {
                case "SUBMIT" -> "newdoc".equals(kind)
                        ? uploadNewVersion(username, user, metadata, file, remoteAddress)
                        : uploadOne(username, user, metadata, file, remoteAddress, action);
                case "DRAFT" -> uploadOne(username, user, metadata, file, remoteAddress, action);
                case "DELETE" -> deleteDraft(username, metadata, remoteAddress);
                case "EDITDRAFT", "EDIT_DRAFT" ->
                        replaceDraft(username, user, metadata, file, remoteAddress);
                default -> rejectedWithMessage(metadata, "Unsupported document action: " + metadata.action());
            });
        }
        return new DocumentUploadResponse(List.copyOf(results));
    }

    private DocumentUploadResponse performMetadataUpdates(
            String username,
            UploadUser user,
            DocumentUploadBatchMetadata batch,
            String kind,
            String remoteAddress) {
        List<DocumentUploadResult> results = new ArrayList<>(batch.documents().size());
        for (DocumentUploadMetadata metadata : batch.documents()) {
            if (metadata == null) {
                results.add(rejected(null, MessageKeys.DOCUMENT_UPLOAD_METADATA_MISSING));
            } else {
                results.add(updateMetadata(username, user, metadata, kind, remoteAddress));
            }
        }
        return new DocumentUploadResponse(List.copyOf(results));
    }

    private DocumentUploadResult updateMetadata(
            String username,
            UploadUser user,
            DocumentUploadMetadata metadata,
            String kind,
            String remoteAddress) {
        try {
            validateMaintenanceIdentity(metadata);
            validateMetadata(metadata, false);
            LocalDate documentDate = parseDate(metadata.documentDate(), "documentDate");
            LocalDate expiryDate = parseOptionalDate(metadata.expiryDate(), "expiryDate");
            validateDateOrder(documentDate, expiryDate);
            AuthorizedDocumentType documentType = validateTargetDocumentMetadata(username, user, metadata);
            boolean automaticApproval = "AUTO".equalsIgnoreCase(documentType.approvalType().trim());
            String requestedStatus = metadata.approvalStatus() == null
                    ? ""
                    : metadata.approvalStatus().trim().toUpperCase(Locale.ROOT);
            String updatedApprovalStatus = null;
            if ("edit".equals(kind) && "I".equals(requestedStatus)) {
                updatedApprovalStatus = automaticApproval ? "A" : "P";
            } else if ("edit".equals(kind) && "A".equals(requestedStatus) && automaticApproval) {
                updatedApprovalStatus = "A";
            }
            String workflowType = "edit".equals(kind) ? "DOC_EDIT" : "DOC_REINDEX";
            persistenceService.editOrReindex(new DocumentMaintenanceRecord(
                    metadata.imageId(),
                    metadata.version(),
                    metadata.documentTypeCode(),
                    metadata.referenceCode(),
                    documentDate,
                    expiryDate,
                    metadata.title().trim(),
                    metadata.additionalInformation(),
                    username,
                    workflowType,
                    "edit".equals(kind) ? "Updated document metadata" : "Re-indexed document",
                    "Document metadata updated successfully.",
                    remoteAddress), updatedApprovalStatus);
            return new DocumentUploadResult(
                    metadata.clientDocumentId(),
                    metadata.imageId(),
                    metadata.title().trim(),
                    "UPDATED",
                    messages.get(MessageKeys.DOCUMENT_MAINTENANCE_SUCCESS));
        } catch (DocumentUploadException exception) {
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, metadata.title(),
                    exception.status(), exception.publicMessage());
        } catch (DocumentNotAccessibleException exception) {
            return rejectedWithMessage(
                    metadata, messages.get(MessageKeys.DOCUMENT_NOT_FOUND_OR_UNAUTHORIZED));
        } catch (IllegalArgumentException exception) {
            return rejectedWithMessage(metadata, exception.getMessage());
        }
    }

    private DocumentUploadResult uploadNewVersion(
            String username,
            UploadUser user,
            DocumentUploadMetadata metadata,
            MultipartFile file,
            String remoteAddress) {
        Path quarantinedFile = null;
        Path storedFile = null;
        boolean persisted = false;
        try {
            long imageId = requestedImageId(metadata);
            int currentVersion = metadata.version() == null
                    ? documentUploadRepository.findLatestVisibleVersion(username, imageId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            messages.get(MessageKeys.DOCUMENT_NOT_FOUND_OR_UNAUTHORIZED)))
                    : metadata.version();
            if (currentVersion < 1 || currentVersion == Integer.MAX_VALUE) {
                throw rejected(MessageKeys.DOCUMENT_UPLOAD_METADATA_FIELDS_INVALID);
            }
            validateMetadata(metadata, false);
            LocalDate documentDate = parseDate(metadata.documentDate(), "documentDate");
            LocalDate expiryDate = parseOptionalDate(metadata.expiryDate(), "expiryDate");
            validateDateOrder(documentDate, expiryDate);
            AuthorizedDocumentType documentType = validateTargetDocumentMetadata(username, user, metadata);

            String extension = fileValidator.validateAndGetExtension(file);
            String actualFileName = actualFileName(file);
            int newVersion = currentVersion + 1;
            String storedFileName = imageId + "_" + newVersion + "." + extension;
            LocalDate storageDate = LocalDate.now();
            String relativeDirectory = "/" + storageDate.getYear()
                    + "/" + storageDate.format(MONTH_DIRECTORY)
                    + "/" + String.format(Locale.ROOT, "%02d", storageDate.getDayOfMonth());
            Path relativePath = Path.of(
                    Integer.toString(storageDate.getYear()),
                    storageDate.format(MONTH_DIRECTORY),
                    String.format(Locale.ROOT, "%02d", storageDate.getDayOfMonth()));
            Path targetDirectory = storagePath.resolve(relativePath).normalize();
            Files.createDirectories(targetDirectory);
            storedFile = targetDirectory.resolve(storedFileName).normalize();
            ensureWithinStorage(storedFile);

            FileDetails fileDetails;
            if (clamAvEnabled) {
                Files.createDirectories(quarantinePath);
                quarantinedFile = quarantinePath.resolve(UUID.randomUUID() + ".quarantine");
                fileDetails = copyAndHash(file.getInputStream(), quarantinedFile);
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
                moveApprovedFile(quarantinedFile, storedFile);
                quarantinedFile = null;
            } else {
                fileDetails = copyAndHash(file.getInputStream(), storedFile);
                fileValidator.validateSignature(storedFile, extension);
            }

            String approvalStatus = "AUTO".equalsIgnoreCase(documentType.approvalType().trim()) ? "A" : "P";
            persistenceService.createNewVersion(
                    new DocumentMaintenanceRecord(
                            imageId, currentVersion, metadata.documentTypeCode(), metadata.referenceCode(),
                            documentDate, expiryDate, metadata.title().trim(), metadata.additionalInformation(),
                            username, "DOC_NEWDOC", "Created new document version",
                            "New document version uploaded successfully.", remoteAddress),
                    newVersion, relativeDirectory, storedFileName, actualFileName, extension,
                    fileDetails.size(), fileDetails.sha256(), approvalStatus);
            persisted = true;
            boolean autoApproved = "A".equals(approvalStatus);
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), imageId, metadata.title().trim(),
                    autoApproved ? "APPROVED" : "PENDING_APPROVAL",
                    messages.get(autoApproved
                            ? MessageKeys.DOCUMENT_UPLOAD_SUCCESS_APPROVED
                            : MessageKeys.DOCUMENT_UPLOAD_SUCCESS_PENDING));
        } catch (DocumentUploadException exception) {
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, metadata.title(),
                    exception.status(), exception.publicMessage());
        } catch (DocumentNotAccessibleException exception) {
            return rejectedWithMessage(
                    metadata, messages.get(MessageKeys.DOCUMENT_NOT_FOUND_OR_UNAUTHORIZED));
        } catch (IllegalArgumentException exception) {
            return rejectedWithMessage(metadata, exception.getMessage());
        } catch (Exception exception) {
            log.error("New document version upload failed; imageId={}, username={}",
                    metadata.imageId(), username, exception);
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

    private DocumentUploadResult replaceDraft(
            String username,
            UploadUser user,
            DocumentUploadMetadata metadata,
            MultipartFile file,
            String remoteAddress) {
        Path stagedFile = null;
        Path priorBackup = null;
        boolean persisted = false;
        try {
            validateMaintenanceIdentity(metadata);
            validateMetadata(metadata, false);
            LocalDate documentDate = parseDate(metadata.documentDate(), "documentDate");
            LocalDate expiryDate = parseOptionalDate(metadata.expiryDate(), "expiryDate");
            validateDateOrder(documentDate, expiryDate);
            AuthorizedDocumentType documentType = validateTargetDocumentMetadata(username, user, metadata);
            DocumentStorageRecord current = persistenceService.findVisibleStorageRecord(
                            username, metadata.imageId(), metadata.version())
                    .orElseThrow(DocumentNotAccessibleException::new);
            Path currentFile = resolveStoredFile(current);
            if (!Files.isRegularFile(currentFile) || !Files.isReadable(currentFile)) {
                throw new DocumentUploadException("NOT_FOUND", messages.get(MessageKeys.DOCUMENT_FILE_NOT_FOUND));
            }

            String extension = fileValidator.validateAndGetExtension(file);
            String actualFileName = actualFileName(file);
            String storedFileName = metadata.imageId() + "_" + metadata.version() + "." + extension;
            LocalDate storageDate = LocalDate.now();
            String relativeDirectory = "/" + storageDate.getYear()
                    + "/" + storageDate.format(MONTH_DIRECTORY)
                    + "/" + String.format(Locale.ROOT, "%02d", storageDate.getDayOfMonth());
            Path relativePath = Path.of(
                    Integer.toString(storageDate.getYear()),
                    storageDate.format(MONTH_DIRECTORY),
                    String.format(Locale.ROOT, "%02d", storageDate.getDayOfMonth()));
            Path targetDirectory = storagePath.resolve(relativePath).normalize();
            Files.createDirectories(targetDirectory);
            Path targetFile = targetDirectory.resolve(storedFileName).normalize();
            ensureWithinStorage(targetFile);
            if (Files.exists(targetFile) && !targetFile.equals(currentFile)) {
                throw new DocumentUploadException("CONFLICT", messages.get(MessageKeys.DOCUMENT_FILE_CONFLICT));
            }

            FileDetails fileDetails;
            if (clamAvEnabled) {
                Files.createDirectories(quarantinePath);
                stagedFile = quarantinePath.resolve(UUID.randomUUID() + ".quarantine");
                fileDetails = copyAndHash(file.getInputStream(), stagedFile);
                fileValidator.validateSignature(stagedFile, extension);
                scanFile(stagedFile);
            } else {
                stagedFile = targetDirectory.resolve(UUID.randomUUID() + ".upload");
                fileDetails = copyAndHash(file.getInputStream(), stagedFile);
                fileValidator.validateSignature(stagedFile, extension);
            }

            priorBackup = currentFile.resolveSibling(currentFile.getFileName() + "." + UUID.randomUUID() + ".backup");
            Files.move(currentFile, priorBackup);
            try {
                moveApprovedFile(stagedFile, targetFile);
                stagedFile = null;
                String approvalStatus = "AUTO".equalsIgnoreCase(documentType.approvalType().trim()) ? "A" : "P";
                persistenceService.replaceDraft(
                        new DocumentMaintenanceRecord(
                                metadata.imageId(), metadata.version(), metadata.documentTypeCode(),
                                metadata.referenceCode(), documentDate, expiryDate, metadata.title().trim(),
                                metadata.additionalInformation(), username, "DOC_UPLOAD",
                                "Replaced draft document file", "Draft document file replaced successfully.",
                                remoteAddress),
                        relativeDirectory, storedFileName, actualFileName, extension,
                        fileDetails.size(), fileDetails.sha256(), approvalStatus);
                persisted = true;
                deleteQuietly(priorBackup);
                priorBackup = null;
                boolean autoApproved = "A".equals(approvalStatus);
                return new DocumentUploadResult(
                        metadata.clientDocumentId(), metadata.imageId(), metadata.title().trim(),
                        autoApproved ? "APPROVED" : "PENDING_APPROVAL",
                        messages.get(autoApproved
                                ? MessageKeys.DOCUMENT_UPLOAD_SUCCESS_APPROVED
                                : MessageKeys.DOCUMENT_UPLOAD_SUCCESS_PENDING));
            } catch (Exception exception) {
                deleteQuietly(targetFile);
                Files.move(priorBackup, currentFile, StandardCopyOption.REPLACE_EXISTING);
                priorBackup = null;
                throw exception;
            }
        } catch (DocumentUploadException exception) {
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, metadata.title(),
                    exception.status(), exception.publicMessage());
        } catch (DocumentNotAccessibleException exception) {
            return rejectedWithMessage(
                    metadata, messages.get(MessageKeys.DOCUMENT_NOT_FOUND_OR_UNAUTHORIZED));
        } catch (IllegalArgumentException exception) {
            return rejectedWithMessage(metadata, exception.getMessage());
        } catch (Exception exception) {
            log.error("Draft document replacement failed; imageId={}, username={}",
                    metadata.imageId(), username, exception);
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, metadata.title(), "FAILED",
                    messages.get(MessageKeys.DOCUMENT_UPLOAD_FAILURE));
        } finally {
            deleteQuietly(stagedFile);
            if (!persisted && priorBackup != null && Files.exists(priorBackup)) {
                log.error("Draft file backup remains after failed replacement; backupPath={}", priorBackup);
            }
        }
    }

    private DocumentUploadResult deleteDraft(
            String username,
            DocumentUploadMetadata metadata,
            String remoteAddress) {
        Path backupFile = null;
        try {
            validateMaintenanceIdentity(metadata);
            DocumentStorageRecord current = persistenceService.findVisibleStorageRecord(
                            username, metadata.imageId(), metadata.version())
                    .orElseThrow(DocumentNotAccessibleException::new);
            Path currentFile = resolveStoredFile(current);
            if (!Files.isRegularFile(currentFile) || !Files.isReadable(currentFile)) {
                throw new DocumentUploadException("NOT_FOUND", messages.get(MessageKeys.DOCUMENT_FILE_NOT_FOUND));
            }
            backupFile = currentFile.resolveSibling(
                    currentFile.getFileName() + "." + UUID.randomUUID() + ".delete");
            Files.move(currentFile, backupFile);
            try {
                persistenceService.deleteDocument(
                        username, metadata.imageId(), metadata.version(), remoteAddress);
                deleteQuietly(backupFile);
                backupFile = null;
                return new DocumentUploadResult(
                        metadata.clientDocumentId(), metadata.imageId(), metadata.title(),
                        "DELETED", messages.get(MessageKeys.DOCUMENT_DELETED));
            } catch (Exception exception) {
                Files.move(backupFile, currentFile, StandardCopyOption.REPLACE_EXISTING);
                backupFile = null;
                throw exception;
            }
        } catch (DocumentNotAccessibleException exception) {
            return rejectedWithMessage(
                    metadata, messages.get(MessageKeys.DOCUMENT_NOT_FOUND_OR_UNAUTHORIZED));
        } catch (DocumentUploadException exception) {
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, metadata.title(),
                    exception.status(), exception.publicMessage());
        } catch (IllegalArgumentException exception) {
            return rejectedWithMessage(metadata, exception.getMessage());
        } catch (Exception exception) {
            log.error("Draft document deletion failed; imageId={}, username={}",
                    metadata.imageId(), username, exception);
            return new DocumentUploadResult(
                    metadata.clientDocumentId(), null, metadata.title(), "FAILED",
                    messages.get(MessageKeys.DOCUMENT_UPLOAD_FAILURE));
        } finally {
            if (backupFile != null && Files.exists(backupFile)) {
                log.error("Deleted document backup remains after failed deletion; backupPath={}", backupFile);
            }
        }
    }

    private DocumentUploadResult uploadOne(
            String username,
            UploadUser user,
            DocumentUploadMetadata metadata,
            MultipartFile file,
            String remoteAddress,
            String action) {
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
                        fileDetails, relativeDirectory, remoteAddress, action);
            } else {
                Path targetDirectory = storagePath.resolve(relativePath).normalize();
                Files.createDirectories(targetDirectory);
                storedFile = targetDirectory.resolve(storedFileName).normalize();
                ensureWithinStorage(storedFile);
                FileDetails fileDetails = copyAndHash(file.getInputStream(), storedFile);
                fileValidator.validateSignature(storedFile, extension);
                internalDocumentId = persistDocument(username, metadata, documentType, documentDate, expiryDate,
                        imageId, INITIAL_DOCUMENT_VERSION, storedFileName, actualFileName, extension,
                        fileDetails, relativeDirectory, remoteAddress, action);
            }

            persisted = true;
            log.info("Document upload completed; documentId={}, imageId={}, clientDocumentId={}, username={}",
                    internalDocumentId, imageId, metadata.clientDocumentId(), username);
            boolean autoApproved = "AUTO".equalsIgnoreCase(documentType.approvalType().trim());
            boolean draft = "DRAFT".equals(action);
            return new DocumentUploadResult(
                    metadata.clientDocumentId(),
                    imageId,
                    metadata.title().trim(),
                    draft ? "DRAFT" : autoApproved ? "APPROVED" : "PENDING_APPROVAL",
                    draft ? messages.get(MessageKeys.DOCUMENT_DRAFT_SAVED)
                            : messages.get(autoApproved
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
            String remoteAddress,
            String action) {
        String approvalStatus = "DRAFT".equals(action)
                ? "I"
                : "AUTO".equalsIgnoreCase(documentType.approvalType().trim()) ? "A" : "P";
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
        ), action);
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

    private MultipartFile resolveActionFile(
            DocumentUploadMetadata metadata,
            List<MultipartFile> orderedFiles,
            Map<String, MultipartFile> namedFiles,
            Set<String> usedFileParts,
            int orderedFileIndex) {
        if (metadata.filePart() != null && !metadata.filePart().isBlank()) {
            String filePart = metadata.filePart().trim();
            if (!usedFileParts.add(filePart)) {
                return null;
            }
            return namedFiles.get(filePart);
        }
        return orderedFileIndex < orderedFiles.size() ? orderedFiles.get(orderedFileIndex) : null;
    }

    private void validateActionFileParts(
            DocumentUploadBatchMetadata batch,
            List<MultipartFile> orderedFiles,
            Map<String, MultipartFile> namedFiles) {
        boolean hasNamedParts = namedFiles.keySet().stream().anyMatch(part -> !"files".equals(part));
        if (hasNamedParts) {
            if (!orderedFiles.isEmpty() || namedFiles.containsKey("files")) {
                throw rejected(MessageKeys.DOCUMENT_UPLOAD_FILE_PARTS_MODE_INVALID);
            }
            Set<String> requestedParts = new HashSet<>();
            for (DocumentUploadMetadata metadata : batch.documents()) {
                if (metadata == null) {
                    continue;
                }
                boolean needsFile = requiresFile(actionOf(metadata, batch));
                String part = metadata.filePart() == null ? "" : metadata.filePart().trim();
                if (needsFile != !part.isBlank() || (!part.isBlank() && !requestedParts.add(part))) {
                    throw rejected(MessageKeys.DOCUMENT_UPLOAD_FILE_PARTS_INVALID);
                }
            }
            if (!requestedParts.equals(namedFiles.keySet())) {
                throw rejected(MessageKeys.DOCUMENT_UPLOAD_FILE_PARTS_UNREFERENCED);
            }
            return;
        }

        if (batch.documents().stream()
                .anyMatch(metadata -> metadata != null && !isBlank(metadata.filePart()))) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_FILE_PARTS_MODE_INVALID);
        }
        long requiredFileCount = batch.documents().stream()
                .filter(metadata -> metadata != null && requiresFile(actionOf(metadata, batch)))
                .count();
        if (requiredFileCount != orderedFiles.size()
                || namedFiles.keySet().stream().anyMatch(part -> !"files".equals(part))) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_FILE_PARTS_MODE_INVALID);
        }
    }

    private String actionOf(DocumentUploadMetadata metadata, DocumentUploadBatchMetadata batch) {
        if (batch.kind() != null && "editdraft".equalsIgnoreCase(batch.kind().trim())) {
            return "EDIT_DRAFT";
        }
        String action = metadata.action();
        if (action == null || action.isBlank()) {
            action = batch.action();
        }
        return action == null || action.isBlank()
                ? "SUBMIT"
                : action.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private boolean requiresFile(String action) {
        return "SUBMIT".equals(action) || "DRAFT".equals(action)
                || "EDITDRAFT".equals(action) || "EDIT_DRAFT".equals(action);
    }

    private Path resolveStoredFile(DocumentStorageRecord document) {
        if (document.relativePath() == null || document.storedFileName() == null
                || document.storedFileName().isBlank()) {
            throw new DocumentUploadException("NOT_FOUND", messages.get(MessageKeys.DOCUMENT_FILE_NOT_FOUND));
        }
        String relativePathValue = document.relativePath().replace('\\', '/');
        while (relativePathValue.startsWith("/")) {
            relativePathValue = relativePathValue.substring(1);
        }
        if (relativePathValue.isBlank()) {
            throw new DocumentUploadException("NOT_FOUND", messages.get(MessageKeys.DOCUMENT_FILE_NOT_FOUND));
        }
        Path relativeDirectory = Path.of(relativePathValue).normalize();
        Path fileName = Path.of(document.storedFileName());
        if (relativeDirectory.isAbsolute() || relativeDirectory.startsWith("..")
                || fileName.isAbsolute() || fileName.getNameCount() != 1 || fileName.startsWith("..")) {
            throw new DocumentUploadException("NOT_FOUND", messages.get(MessageKeys.DOCUMENT_FILE_NOT_FOUND));
        }
        Path resolved = storagePath.resolve(relativeDirectory).resolve(fileName).normalize();
        if (!resolved.startsWith(storagePath)) {
            throw new DocumentUploadException("NOT_FOUND", messages.get(MessageKeys.DOCUMENT_FILE_NOT_FOUND));
        }
        try {
            Path realRoot = storagePath.toRealPath();
            Path realFile = resolved.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                throw new DocumentUploadException(
                        "NOT_FOUND", messages.get(MessageKeys.DOCUMENT_FILE_NOT_FOUND));
            }
            return realFile;
        } catch (IOException exception) {
            throw new DocumentUploadException(
                    "NOT_FOUND", messages.get(MessageKeys.DOCUMENT_FILE_NOT_FOUND), exception);
        }
    }

    private void scanFile(Path file) throws IOException {
        try {
            clamAvScanner.scan(file);
        } catch (ClamAvScanner.InfectedFileException exception) {
            throw new DocumentUploadException(
                    "REJECTED", messages.get(MessageKeys.DOCUMENT_UPLOAD_MALWARE_DETECTED), exception);
        } catch (IOException exception) {
            throw new DocumentUploadException(
                    "FAILED", messages.get(MessageKeys.DOCUMENT_UPLOAD_SCANNER_UNAVAILABLE), exception);
        }
    }

    private void validateNoFiles(
            DocumentUploadBatchMetadata batch,
            List<MultipartFile> orderedFiles,
            Map<String, MultipartFile> namedFiles) {
        if (!orderedFiles.isEmpty() || !namedFiles.isEmpty()
                || batch.documents().stream().anyMatch(document ->
                document != null && !isBlank(document.filePart()))) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_FILE_PARTS_MODE_INVALID);
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
        validateMetadata(metadata, true);
    }

    private void validateMetadata(DocumentUploadMetadata metadata, boolean requireReferenceType) {
        if (metadata == null
                || isBlank(metadata.referenceCode())
                || (requireReferenceType && (isBlank(metadata.referenceType())
                || !REFERENCE_TYPES.contains(metadata.referenceType())))
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

    private void validateMaintenanceIdentity(DocumentUploadMetadata metadata) {
        if (metadata.imageId() == null || metadata.imageId() < 1
                || metadata.version() == null || metadata.version() < 1) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_METADATA_FIELDS_INVALID);
        }
    }

    private long requestedImageId(DocumentUploadMetadata metadata) {
        Long imageId = metadata.imageId() != null ? metadata.imageId() : metadata.clientDocumentId();
        if (imageId == null || imageId < 1) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_METADATA_FIELDS_INVALID);
        }
        return imageId;
    }

    private AuthorizedDocumentType validateTargetDocumentMetadata(
            String username, UploadUser user, DocumentUploadMetadata metadata) {
        if (!documentUploadRepository.hasEntityVisibility(user.userCode(), metadata.referenceCode())) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_REFERENCE_UNAUTHORIZED);
        }
        AuthorizedDocumentType documentType = documentUploadRepository.findAuthorizedDocumentType(
                        username, metadata.documentTypeCode(), metadata.categoryCode())
                .orElseThrow(() -> rejected(MessageKeys.DOCUMENT_UPLOAD_TYPE_UNAUTHORIZED));
        validateAgainstDocumentType(metadata, documentType);
        return documentType;
    }

    private LocalDate parseOptionalDate(String value, String fieldName) {
        return value == null || value.isBlank() ? null : parseDate(value, fieldName);
    }

    private void validateDateOrder(LocalDate documentDate, LocalDate expiryDate) {
        if (expiryDate != null && expiryDate.isBefore(documentDate)) {
            throw rejected(MessageKeys.DOCUMENT_UPLOAD_EXPIRY_BEFORE_DOCUMENT);
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

    private DocumentUploadResult rejectedWithMessage(
            DocumentUploadMetadata metadata, String message) {
        return new DocumentUploadResult(
                metadata.clientDocumentId(),
                null,
                metadata.title(),
                "REJECTED",
                message);
    }

    private DocumentUploadException rejected(String messageKey) {
        return new DocumentUploadException("REJECTED", messages.get(messageKey));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
