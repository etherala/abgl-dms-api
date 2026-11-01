package com.abgl.dms.documentupload;

public record AuthorizedDocumentType(
        long documentTypeId,
        String documentTypeCode,
        String approvalType,
        boolean criticalIndicator,
        boolean expiryDateMandatory
) {}
