package com.abgl.dms.controller;

import com.abgl.dms.vaultmaintenance.VaultMaintenanceAuditItem;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceInput;
import com.abgl.dms.vaultmaintenance.VaultMaintenancePage;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceRecord;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceRejection;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceSearch;
import com.abgl.dms.vaultmaintenance.VaultMaintenanceService;
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
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/vault/maintenance")
@Validated
public class VaultMaintenanceController {

    private final VaultMaintenanceService service;

    public VaultMaintenanceController(VaultMaintenanceService service) {
        this.service = service;
    }

    @PostMapping("/search")
    public VaultMaintenancePage search(@Valid @RequestBody VaultMaintenanceSearch search) {
        return service.search(search);
    }

    @GetMapping("/records/{vaultId}")
    public VaultMaintenanceRecord getRecord(@PathVariable long vaultId) {
        return service.getRecord(vaultId);
    }

    @PostMapping("/records")
    public VaultMaintenanceRecord create(
            Authentication authentication,
            @Valid @RequestBody VaultMaintenanceInput input,
            HttpServletRequest request) {
        return service.create(input, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/records/draft")
    public VaultMaintenanceRecord createDraft(
            Authentication authentication,
            @Valid @RequestBody VaultMaintenanceInput input,
            HttpServletRequest request) {
        return service.createDraft(input, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/records/{vaultId}/draft")
    public VaultMaintenanceRecord createEditDraft(
            Authentication authentication,
            @PathVariable long vaultId,
            @Valid @RequestBody VaultMaintenanceInput input,
            HttpServletRequest request) {
        return service.createEditDraft(
                vaultId, input, authentication.getName(), request.getRemoteAddr());
    }

    @PutMapping("/records/{vaultId}")
    public VaultMaintenanceRecord update(
            Authentication authentication,
            @PathVariable long vaultId,
            @Valid @RequestBody VaultMaintenanceInput input,
            HttpServletRequest request) {
        return service.update(vaultId, input, authentication.getName(), request.getRemoteAddr());
    }

    @GetMapping("/workbench/{wbId}")
    public VaultMaintenanceRecord getWorkbench(@PathVariable UUID wbId) {
        return service.getWorkbench(wbId);
    }

    @PutMapping("/workbench/{wbId}/draft")
    public VaultMaintenanceRecord updateDraft(
            Authentication authentication,
            @PathVariable UUID wbId,
            @Valid @RequestBody VaultMaintenanceInput input,
            HttpServletRequest request) {
        return service.updateDraft(wbId, input, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/workbench/{wbId}/submit")
    public VaultMaintenanceRecord submit(
            Authentication authentication,
            @PathVariable UUID wbId,
            HttpServletRequest request) {
        return service.submit(wbId, authentication.getName(), request.getRemoteAddr());
    }

    @GetMapping("/pending")
    public Map<String, List<VaultMaintenanceRecord>> getPending() {
        return Map.of("items", service.getPending());
    }

    @PostMapping("/workbench/{wbId}/approve")
    public VaultMaintenanceRecord approve(
            Authentication authentication,
            @PathVariable UUID wbId,
            HttpServletRequest request) {
        return service.approve(wbId, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/workbench/{wbId}/reject")
    public VaultMaintenanceRecord reject(
            Authentication authentication,
            @PathVariable UUID wbId,
            @Valid @RequestBody VaultMaintenanceRejection rejection,
            HttpServletRequest request) {
        return service.reject(
                wbId, authentication.getName(), rejection.reason(), request.getRemoteAddr());
    }

    @DeleteMapping("/workbench/{wbId}")
    public VaultMaintenanceRecord deleteDraft(
            Authentication authentication,
            @PathVariable UUID wbId,
            HttpServletRequest request) {
        return service.deleteDraft(wbId, authentication.getName(), request.getRemoteAddr());
    }

    @GetMapping("/records/{vaultId}/audit")
    public List<VaultMaintenanceAuditItem> getAudit(@PathVariable long vaultId) {
        return service.getAudit(vaultId);
    }

    @GetMapping("/workbench/{wbId}/audit")
    public List<VaultMaintenanceAuditItem> getWorkbenchAudit(@PathVariable UUID wbId) {
        return service.getWorkbenchAudit(wbId);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException exception) {
        return error(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, exception);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateKey(DuplicateKeyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Vault record conflicts with an existing tray code or identifier"));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, RuntimeException exception) {
        return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
    }
}
