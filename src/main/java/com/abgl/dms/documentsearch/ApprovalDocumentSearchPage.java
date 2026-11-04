package com.abgl.dms.documentsearch;

import java.util.List;

public record ApprovalDocumentSearchPage(
        List<DocumentSearchItem> items,
        long totalElements,
        int page,
        int pageSize,
        int totalPages
) {}
