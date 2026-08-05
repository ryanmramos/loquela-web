package com.example.whatsanki.controller;

import com.example.whatsanki.dto.BrowserSettingsDto;
import com.example.whatsanki.dto.UpdateBrowserSettingsRequestDto;
import com.example.whatsanki.service.BrowserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ExternalEndpointSecurity(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF_OWNER_SCOPED)
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class BrowserSettingsController {
    private final BrowserSettingsService browserSettingsService;

    @GetMapping
    @Operation(operationId = "getBrowserSettings", summary = "Read Google metadata and user preferences")
    public ResponseEntity<BrowserSettingsDto> get(Authentication authentication) {
        return ResponseEntity.ok(browserSettingsService.get(authentication.getName()));
    }

    @PatchMapping
    @Operation(operationId = "updateBrowserSettings", summary = "Update future-work settings")
    public ResponseEntity<BrowserSettingsDto> update(
            Authentication authentication,
            @Valid @RequestBody UpdateBrowserSettingsRequestDto request
    ) {
        return ResponseEntity.ok(browserSettingsService.update(authentication.getName(), request));
    }
}
