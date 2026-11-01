package com.abgl.dms.businessdate;

import com.abgl.dms.repository.BusinessDateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BusinessDateService {

    private final BusinessDateRepository businessDateRepository;

    public BusinessDateService(BusinessDateRepository businessDateRepository) {
        this.businessDateRepository = businessDateRepository;
    }

    public BusinessDateResponse getBusinessDate(String branchCode) {
        return businessDateRepository.findActiveBusinessDate(branchCode)
                .map(businessDate -> new BusinessDateResponse(branchCode, businessDate))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active business date found for branch"));
    }
}
