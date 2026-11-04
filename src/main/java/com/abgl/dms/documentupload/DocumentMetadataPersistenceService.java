package com.abgl.dms.documentupload;

import com.abgl.dms.repository.DocumentUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DocumentMetadataPersistenceService {

    private final DocumentUploadRepository documentUploadRepository;

    public DocumentMetadataPersistenceService(DocumentUploadRepository documentUploadRepository) {
        this.documentUploadRepository = documentUploadRepository;
    }

    @Transactional
    public long persist(DocumentRecord document, String action) {
        return documentUploadRepository.insertDocument(document, action);
    }

    @Transactional
    public void editOrReindex(DocumentMaintenanceRecord document, String approvalStatus) {
        documentUploadRepository.editOrReindexDocument(document, approvalStatus);
    }

    @Transactional
    public void decideDocuments(
            String username,
            List<DocumentApprovalRecord> documents,
            String approvalStatus,
            String ipAddress) {
        for (DocumentApprovalRecord document : documents) {
            documentUploadRepository.decideDocument(
                    username, document, approvalStatus, ipAddress);
        }
    }

    @Transactional
    public void createNewVersion(
            DocumentMaintenanceRecord document,
            int newVersion,
            String relativePath,
            String storedFileName,
            String actualFileName,
            String extension,
            long fileSize,
            String fileHash,
            String approvalStatus) {
        documentUploadRepository.createNewVersion(
                document, newVersion, relativePath, storedFileName,
                actualFileName, extension, fileSize, fileHash, approvalStatus);
    }

    @Transactional
    public void replaceDraft(
            DocumentMaintenanceRecord document,
            String relativePath,
            String storedFileName,
            String actualFileName,
            String extension,
            long fileSize,
            String fileHash,
            String approvalStatus) {
        documentUploadRepository.replaceDraftDocument(
                document, relativePath, storedFileName, actualFileName,
                extension, fileSize, fileHash, approvalStatus);
    }

    @Transactional
    public void deleteDocument(String username, long imageId, int version, String ipAddress) {
        documentUploadRepository.deleteDocument(username, imageId, version, ipAddress);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentStorageRecord> findVisibleStorageRecord(
            String username, long imageId, int version) {
        return documentUploadRepository.findVisibleStorageRecord(username, imageId, version);
    }

    @Transactional
    public void archive(String username, long imageId, int version, String ipAddress) {
        documentUploadRepository.archiveDocument(username, imageId, version, ipAddress);
    }
}
