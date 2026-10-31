package com.abgl.dms.documentupload;

public record DocumentUploadMetadata(
        Long clientDocumentId,
        String referenceCode,
        String referenceType,
        String categoryCode,
        String documentTypeCode,
        String title,
        String documentDate,
        String expiryDate,
        String additionalInformation,
        Boolean criticalIndicator,
        String approvalType,
        String filePart,
        String branchCode
) {}
