package com.abgl.dms.documentupload;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentRecord(
        String imageId,
        long documentCollectionId,
        long documentTypeId,
        long folderId,
        String referenceType,
        String referenceCode,
        LocalDate documentDate,
        LocalDate expiryDate,
        String title,
        String additionalInformation,
        UUID createdBy,
        String entityCode
) {}
