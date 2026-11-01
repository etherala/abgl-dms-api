package com.abgl.dms.documentupload;

import java.time.LocalDate;

public record DocumentRecord(
        long imageId,
        int version,
        String documentType,
        String referenceCode,
        LocalDate documentDate,
        LocalDate expiryDate,
        String title,
        String additionalInformation,
        String approvalStatus,
        String createdBy,
        String docStorePath,
        String docFileName,
        String docActualFileName,
        String docExtension,
        long docSize,
        String docHash,
        String ipAddress
) {}
