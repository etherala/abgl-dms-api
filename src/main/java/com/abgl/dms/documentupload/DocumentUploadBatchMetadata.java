package com.abgl.dms.documentupload;

import java.util.List;

public record DocumentUploadBatchMetadata(
        String kind,
        String action,
        List<DocumentUploadMetadata> documents
) {}
