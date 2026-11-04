package com.abgl.dms.documentsearch;

public record StoredDocument(
        long imageId,
        int version,
        String relativePath,
        String storedFileName,
        String actualFileName,
        String extension
) {}
