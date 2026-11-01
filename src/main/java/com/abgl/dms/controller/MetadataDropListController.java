package com.abgl.dms.controller;

import com.abgl.dms.metadata.MetadataDropListItem;
import com.abgl.dms.metadata.MetadataDropListService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metadata/drop-list")
@Validated
public class MetadataDropListController {

    private final MetadataDropListService metadataDropListService;

    public MetadataDropListController(MetadataDropListService metadataDropListService) {
        this.metadataDropListService = metadataDropListService;
    }

    @GetMapping
    public Map<String, List<MetadataDropListItem>> getDropList(
            @RequestParam("md_type") @NotBlank @Size(max = 100) String metadataType) {
        return Map.of("items", metadataDropListService.getValues(metadataType.trim()));
    }
}
