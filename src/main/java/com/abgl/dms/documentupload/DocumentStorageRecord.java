package com.abgl.dms.documentupload;

public record DocumentStorageRecord(
        long imageId,
        int version,
        String relativePath,
        String storedFileName
) {}
