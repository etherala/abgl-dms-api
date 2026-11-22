package com.abgl.dms.documentsearch;

import com.abgl.dms.repository.DocumentSearchRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ApprovalDocumentSearchService {

    private final DocumentSearchRepository repository;

    public ApprovalDocumentSearchService(DocumentSearchRepository repository) {
        this.repository = repository;
    }

    public ApprovalDocumentSearchPage search(String username, ApprovalDocumentSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Approval search request is required");
        }
        validateDateRange(request.uploadDateFrom(), request.uploadDateTo(), "upload date");
        validateDateRange(request.documentDateFrom(), request.documentDateTo(), "document date");
        validateDateRange(request.expiryDateFrom(), request.expiryDateTo(), "expiry date");

        int page = request.page() == null ? DocumentSearchService.DEFAULT_PAGE : request.page();
        int pageSize = request.pageSize() == null
                ? DocumentSearchService.DEFAULT_PAGE_SIZE
                : request.pageSize();
        if (page < 1) {
            throw new IllegalArgumentException("Page must be greater than or equal to 1");
        }
        if (pageSize < 1 || pageSize > DocumentSearchService.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and " + DocumentSearchService.MAX_PAGE_SIZE);
        }

        long totalElements = repository.countVisibleApprovalDocuments(username, request);
        long offset = ((long) page - 1) * pageSize;
        var items = repository.searchVisibleApprovalDocuments(username, request, pageSize, offset);
        int totalPages = (int) ((totalElements + pageSize - 1) / pageSize);
        return new ApprovalDocumentSearchPage(items, totalElements, page, pageSize, totalPages);
    }

    private void validateDateRange(LocalDate from, LocalDate to, String field) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The " + field + " start date must not be after its end date");
        }
    }
}
