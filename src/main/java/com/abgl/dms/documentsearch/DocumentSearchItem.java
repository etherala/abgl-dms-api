package com.abgl.dms.documentsearch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DocumentSearchItem(
        long documentTypeId,
        String documentTypeCode,
        String approvalType,
        boolean criticalIndicator,
        boolean expiryDateMandatory,
        long imageId,
        int version,
        String referenceCode,
        LocalDate documentDate,
        LocalDate expiryDate,
        String title,
        String additionalInformation,
        String approvalStatus,
        String uploadedBy,
        OffsetDateTime uploadedOn,
        String docStorePath,
        String docFileName,
        long docSize,
        String docActualFileName,
        String entityName,
        String documentTypeName,
        BigDecimal retentionPeriodValue,
        String masterRetentionCode,
        boolean physicalStorageRequired,
        String categoryDescription,
        int dayPending,
        boolean overDue,
        Integer daysRemaining,
        String expiryStatus
) {}
