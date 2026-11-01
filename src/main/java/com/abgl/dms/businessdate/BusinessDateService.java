package com.abgl.dms.businessdate;

import com.abgl.dms.repository.BusinessDateRepository;
import com.abgl.dms.i18n.ApplicationMessages;
import com.abgl.dms.i18n.MessageKeys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BusinessDateService {

    private final BusinessDateRepository businessDateRepository;
    private final ApplicationMessages messages;

    public BusinessDateService(BusinessDateRepository businessDateRepository, ApplicationMessages messages) {
        this.businessDateRepository = businessDateRepository;
        this.messages = messages;
    }

    public BusinessDateResponse getBusinessDate(String branchCode) {
        return businessDateRepository.findActiveBusinessDate(branchCode)
                .map(businessDate -> new BusinessDateResponse(branchCode, businessDate))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, messages.get(MessageKeys.BUSINESS_DATE_NOT_FOUND)));
    }
}
