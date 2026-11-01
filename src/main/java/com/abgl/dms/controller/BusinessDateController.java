package com.abgl.dms.controller;

import com.abgl.dms.businessdate.BusinessDateResponse;
import com.abgl.dms.businessdate.BusinessDateService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-date")
@Validated
public class BusinessDateController {

    private final BusinessDateService businessDateService;

    public BusinessDateController(BusinessDateService businessDateService) {
        this.businessDateService = businessDateService;
    }

    @GetMapping
    public BusinessDateResponse getBusinessDate(
            @RequestParam @NotBlank @Size(max = 20) String branchCode) {
        return businessDateService.getBusinessDate(branchCode);
    }
}
