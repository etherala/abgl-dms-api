package com.abgl.dms.documentsearch;

import com.abgl.dms.repository.DocumentSearchRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class DocumentSearchService {

    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MAX_PAGE_SIZE = 100;

    private final DocumentSearchRepository repository;

    public DocumentSearchService(DocumentSearchRepository repository) {
        this.repository = repository;
    }

    public DocumentSearchPage search(String username, DocumentSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Search request is required");
        }
        validateDateRange(request.uploadDateFrom(), request.uploadDateTo(), "upload date");
        validateDateRange(request.documentDateFrom(), request.documentDateTo(), "document date");
        validateDateRange(request.expiryDateFrom(), request.expiryDateTo(), "expiry date");
        if (request.maxDaysRemaining() != null && request.maxDaysRemaining() < 0) {
            throw new IllegalArgumentException("Maximum days remaining must not be negative");
        }

        int page = request.page() == null ? DEFAULT_PAGE : request.page();
        int pageSize = request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize();
        if (page < 1) {
            throw new IllegalArgumentException("Page must be greater than or equal to 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        long totalElements = repository.countVisibleDocuments(username, request);
        long offset = ((long) page - 1) * pageSize;
        var items = repository.searchVisibleDocuments(username, request, pageSize, offset);
        int totalPages = (int) ((totalElements + pageSize - 1) / pageSize);
        return new DocumentSearchPage(items, totalElements, page, pageSize, totalPages);
    }

    private void validateDateRange(LocalDate from, LocalDate to, String field) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The " + field + " start date must not be after its end date");
        }
    }
}
