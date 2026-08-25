package com.example.whatsanki.controller;

import com.example.whatsanki.dto.AdminAccessEntryDto;
import com.example.whatsanki.dto.AdminAccessEntryListDto;
import com.example.whatsanki.dto.AdminAccessRole;
import com.example.whatsanki.dto.AdminAccessStatus;
import com.example.whatsanki.dto.CreateAdminAccessEntryRequestDto;
import com.example.whatsanki.dto.UpdateAdminAccessEntryRequestDto;
import com.example.whatsanki.service.AdminAccessService;
import com.example.whatsanki.service.security.BrowserAuthenticationSuccessHandler;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Exposes the administrator-managed Google access allowlist.
 *
 * <p>Mutation methods pass the recent-authentication result to the service because access changes
 * require a fresh sign-in as well as the administrator role.</p>
 */
@RestController
@Validated
@RequiredArgsConstructor
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
@ExternalEndpointSecurity(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF_ADMIN)
@RequestMapping("/api/v1/admin/access-entries")
public class AdminAccessController {
    private static final Duration RECENT_AUTHENTICATION_LIFETIME = Duration.ofMinutes(10);

    private final AdminAccessService adminAccessService;

    @GetMapping
    @Operation(operationId = "listAdminAccessEntries", summary = "List Google access grants")
    public ResponseEntity<AdminAccessEntryListDto> list(
            Authentication authentication,
            @RequestParam(required = false) @Size(max = 320) String search,
            @RequestParam(required = false) AdminAccessRole role,
            @RequestParam(required = false) AdminAccessStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        return ResponseEntity.ok(adminAccessService.list(
                authentication.getName(), search, role, status, page, pageSize
        ));
    }

    @PostMapping
    @Operation(operationId = "createAdminAccessEntry", summary = "Create or reactivate a Google access grant")
    public ResponseEntity<AdminAccessEntryDto> create(
            Authentication authentication,
            HttpSession session,
            @Valid @RequestBody CreateAdminAccessEntryRequestDto request
    ) {
        return ResponseEntity.ok(adminAccessService.create(
                authentication.getName(), request, recentlyAuthenticated(session)
        ));
    }

    @PatchMapping("/{reference}")
    @Operation(operationId = "updateAdminAccessEntry", summary = "Update an existing Google access grant")
    public ResponseEntity<AdminAccessEntryDto> update(
            Authentication authentication,
            HttpSession session,
            @PathVariable UUID reference,
            @Valid @RequestBody UpdateAdminAccessEntryRequestDto request
    ) {
        return ResponseEntity.ok(adminAccessService.update(
                authentication.getName(), reference, request, recentlyAuthenticated(session)
        ));
    }

    private boolean recentlyAuthenticated(HttpSession session) {
        // The timestamp is written after a successful OAuth or local login. Keep this check
        // at the controller boundary so the service receives a small, explicit security fact.
        Object value = session.getAttribute(BrowserAuthenticationSuccessHandler.RECENT_AUTHENTICATION_AT);
        return value instanceof Long epochMillis
                && !Instant.ofEpochMilli(epochMillis)
                .plus(RECENT_AUTHENTICATION_LIFETIME)
                .isBefore(Instant.now());
    }
}
