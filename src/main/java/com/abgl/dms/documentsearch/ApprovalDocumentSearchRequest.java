package com.abgl.dms.documentsearch;

import java.time.LocalDate;

public record ApprovalDocumentSearchRequest(
        String title,
        String referenceCode,
        String categoryCode,
        String documentTypeCode,
        String submittedBy,
        String statusCode,
        LocalDate uploadDateFrom,
        LocalDate uploadDateTo,
        LocalDate documentDateFrom,
        LocalDate documentDateTo,
        LocalDate expiryDateFrom,
        LocalDate expiryDateTo,
        Integer page,
        Integer pageSize
) {}
