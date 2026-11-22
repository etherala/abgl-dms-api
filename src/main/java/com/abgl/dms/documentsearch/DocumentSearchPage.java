package com.abgl.dms.documentsearch;

import java.util.List;

public record DocumentSearchPage(
        List<DocumentSearchItem> items,
        long totalElements,
        int page,
        int pageSize,
        int totalPages
) {}
