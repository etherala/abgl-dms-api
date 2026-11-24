package com.abgl.dms.controller;

import com.abgl.dms.metadata.MetadataMaintenanceAuditItem;
import com.abgl.dms.metadata.MetadataMaintenanceInput;
import com.abgl.dms.metadata.MetadataMaintenancePage;
import com.abgl.dms.metadata.MetadataMaintenanceRecord;
import com.abgl.dms.metadata.MetadataMaintenanceRejection;
import com.abgl.dms.metadata.MetadataMaintenanceSearch;
import com.abgl.dms.metadata.MetadataMaintenanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/metadata/maintenance")
@Validated
public class MetadataMaintenanceController {

    private final MetadataMaintenanceService service;

    public MetadataMaintenanceController(MetadataMaintenanceService service) {
        this.service = service;
    }

    @GetMapping("/types")
    public Map<String, List<String>> getTypes() {
        return Map.of("items", service.getMetadataTypes());
    }

    @PostMapping("/search")
    public MetadataMaintenancePage search(@Valid @RequestBody MetadataMaintenanceSearch search) {
        return service.search(search);
    }

    @GetMapping("/records/{id}")
    public MetadataMaintenanceRecord getRecord(@PathVariable long id) {
        return service.getRecord(id);
    }

    @PostMapping("/records")
    public MetadataMaintenanceRecord create(
            Authentication authentication,
            @Valid @RequestBody MetadataMaintenanceInput input,
            HttpServletRequest request) {
        return service.create(input, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/records/draft")
    public MetadataMaintenanceRecord createDraft(
            Authentication authentication,
            @Valid @RequestBody MetadataMaintenanceInput input,
            HttpServletRequest request) {
        return service.createDraft(input, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/records/{id}/draft")
    public MetadataMaintenanceRecord createEditDraft(
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody MetadataMaintenanceInput input,
            HttpServletRequest request) {
        return service.createEditDraft(id, input, authentication.getName(), request.getRemoteAddr());
    }

    @GetMapping("/workbench/{wbId}")
    public MetadataMaintenanceRecord getWorkbench(@PathVariable UUID wbId) {
        return service.getWorkbench(wbId);
    }

    @PutMapping("/workbench/{wbId}/draft")
    public MetadataMaintenanceRecord updateDraft(
            Authentication authentication,
            @PathVariable UUID wbId,
            @Valid @RequestBody MetadataMaintenanceInput input,
            HttpServletRequest request) {
        return service.updateDraft(wbId, input, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/workbench/{wbId}/submit")
    public MetadataMaintenanceRecord submit(
            Authentication authentication,
            @PathVariable UUID wbId,
            HttpServletRequest request) {
        return service.submit(wbId, authentication.getName(), request.getRemoteAddr());
    }

    @PutMapping("/records/{id}")
    public MetadataMaintenanceRecord update(
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody MetadataMaintenanceInput input,
            HttpServletRequest request) {
        return service.update(id, input, authentication.getName(), request.getRemoteAddr());
    }

    @GetMapping("/pending")
    public Map<String, List<MetadataMaintenanceRecord>> getPending() {
        return Map.of("items", service.getPending());
    }

    @PostMapping("/workbench/{wbId}/approve")
    public MetadataMaintenanceRecord approve(
            Authentication authentication,
            @PathVariable UUID wbId,
            HttpServletRequest request) {
        return service.approve(wbId, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/workbench/{wbId}/reject")
    public MetadataMaintenanceRecord reject(
            Authentication authentication,
            @PathVariable UUID wbId,
            @Valid @RequestBody MetadataMaintenanceRejection rejection,
            HttpServletRequest request) {
        return service.reject(wbId, authentication.getName(), rejection.reason(), request.getRemoteAddr());
    }

    @DeleteMapping("/workbench/{wbId}")
    public MetadataMaintenanceRecord deleteDraft(
            Authentication authentication,
            @PathVariable UUID wbId,
            HttpServletRequest request) {
        return service.deleteDraft(wbId, authentication.getName(), request.getRemoteAddr());
    }

    @DeleteMapping("/records/{id}")
    public MetadataMaintenanceRecord delete(
            Authentication authentication,
            @PathVariable long id,
            HttpServletRequest request) {
        return service.delete(id, authentication.getName(), request.getRemoteAddr());
    }

    @GetMapping("/records/{id}/audit")
    public List<MetadataMaintenanceAuditItem> getAudit(@PathVariable long id) {
        return service.getAudit(id);
    }

    @GetMapping("/workbench/{wbId}/audit")
    public List<MetadataMaintenanceAuditItem> getWorkbenchAudit(@PathVariable UUID wbId) {
        return service.getWorkbenchAudit(wbId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException exception) {
        return error(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler({IllegalStateException.class, DuplicateKeyException.class})
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, exception);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, RuntimeException exception) {
        return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
    }
}
