package com.abgl.dms.documentsearch;

import java.time.LocalDate;

public record DocumentSearchRequest(
        String referenceCode,
        String categoryCode,
        String documentTypeCode,
        String title,
        LocalDate uploadDateFrom,
        LocalDate uploadDateTo,
        LocalDate documentDateFrom,
        LocalDate documentDateTo,
        LocalDate expiryDateFrom,
        LocalDate expiryDateTo,
        String status,
        Integer page,
        Integer pageSize,
        Integer maxDaysRemaining,
        String expiryStatus
) {
    public DocumentSearchRequest(
            String referenceCode,
            String categoryCode,
            String documentTypeCode,
            String title,
            LocalDate uploadDateFrom,
            LocalDate uploadDateTo,
            LocalDate documentDateFrom,
            LocalDate documentDateTo,
            LocalDate expiryDateFrom,
            LocalDate expiryDateTo,
            String status,
            Integer page,
            Integer pageSize) {
        this(referenceCode, categoryCode, documentTypeCode, title, uploadDateFrom, uploadDateTo,
                documentDateFrom, documentDateTo, expiryDateFrom, expiryDateTo, status, page, pageSize, null, null);
    }

    public DocumentSearchRequest(
            String referenceCode,
            String categoryCode,
            String documentTypeCode,
            String title,
            LocalDate uploadDateFrom,
            LocalDate uploadDateTo,
            LocalDate documentDateFrom,
            LocalDate documentDateTo,
            LocalDate expiryDateFrom,
            LocalDate expiryDateTo,
            String status,
            Integer page,
            Integer pageSize,
            Integer maxDaysRemaining) {
        this(referenceCode, categoryCode, documentTypeCode, title, uploadDateFrom, uploadDateTo,
                documentDateFrom, documentDateTo, expiryDateFrom, expiryDateTo, status, page, pageSize,
                maxDaysRemaining, null);
    }
}
