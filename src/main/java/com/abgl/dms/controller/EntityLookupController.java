package com.abgl.dms.controller;

import com.abgl.dms.entitylookup.EntityLookupItem;
import com.abgl.dms.entitylookup.EntityLookupService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/entities")
@Validated
public class EntityLookupController {

    private final EntityLookupService entityLookupService;

    public EntityLookupController(EntityLookupService entityLookupService) {
        this.entityLookupService = entityLookupService;
    }

    @GetMapping
    public List<EntityLookupItem> searchEntities(
            Authentication authentication,
            @RequestParam("search") @NotBlank @Size(max = 100) String prefix) {
        return entityLookupService.searchVisibleEntities(authentication.getName(), prefix);
    }
}
