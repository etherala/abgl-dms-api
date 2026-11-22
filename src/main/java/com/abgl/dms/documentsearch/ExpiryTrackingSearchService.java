package com.abgl.dms.documentsearch;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class ExpiryTrackingSearchService {

    private static final Set<String> EXPIRY_STATUSES = Set.of("DS", "ES", "VE", "NE", "OE");

    private final DocumentSearchService documentSearchService;

    public ExpiryTrackingSearchService(DocumentSearchService documentSearchService) {
        this.documentSearchService = documentSearchService;
    }

    public DocumentSearchPage search(String username, ExpiryTrackingSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Expiry tracking search request is required");
        }
        String expiryStatus = request.expiryStatus();
        if (expiryStatus != null && !expiryStatus.isBlank()) {
            expiryStatus = expiryStatus.trim().toUpperCase(Locale.ROOT);
            if (!EXPIRY_STATUSES.contains(expiryStatus)) {
                throw new IllegalArgumentException("Expiry status must be one of DS, ES, VE, NE, or OE");
            }
        }

        return documentSearchService.search(username, new DocumentSearchRequest(
                request.referenceCode(),
                request.categoryCode(),
                request.documentTypeCode(),
                request.title(),
                request.uploadDateFrom(),
                request.uploadDateTo(),
                request.documentDateFrom(),
                request.documentDateTo(),
                request.expiryDateFrom(),
                request.expiryDateTo(),
                request.status(),
                request.page(),
                request.pageSize(),
                request.maxDaysRemaining(),
                expiryStatus));
    }
}
