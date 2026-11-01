package com.abgl.dms.documentupload;

public record DocumentUploadResult(
        Long clientDocumentId,
        Long documentId,
        String documentTitle,
        String status,
        String message
) {}
