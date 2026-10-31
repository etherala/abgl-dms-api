package com.abgl.dms.documenttype;

public record DocumentType(
        String documentTypeCode,
        String documentCategory,
        String documentTypeName,
        String approvalType,
        boolean criticalIndicator,
        String documentDescription,
        boolean expiryDateMandatory,
        boolean active
) {}
