package com.abgl.dms.documentupload;

import java.time.LocalDate;

public record DocumentMaintenanceRecord(
        long imageId,
        int currentVersion,
        String documentTypeCode,
        String referenceCode,
        LocalDate documentDate,
        LocalDate expiryDate,
        String title,
        String additionalInformation,
        String updatedBy,
        String workflowType,
        String actionDescription,
        String remarks,
        String ipAddress
) {}
