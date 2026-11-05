package com.abgl.dms.documentsearch;

import java.time.LocalDate;

public record ExpiryTrackingSearchRequest(
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
) {}
