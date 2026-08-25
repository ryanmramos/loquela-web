package com.example.whatsanki.controller;

import com.example.whatsanki.dto.BrowserLanguageSettingsDto;
import com.example.whatsanki.dto.UpdateLanguageSettingsRequestDto;
import com.example.whatsanki.service.BrowserLanguageSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reads and updates the language pair used by future lookups and source ingestion. */
@ExternalEndpointSecurity(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF_OWNER_SCOPED)
@RestController
@RequestMapping("/api/v1/settings/languages")
@RequiredArgsConstructor
public class BrowserLanguageSettingsController {

    private final BrowserLanguageSettingsService browserLanguageSettingsService;

    @GetMapping
    @Operation(
            operationId = "getBrowserLanguageSettings",
            summary = "Return the current and supported source/definition language pairs"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Language settings",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BrowserLanguageSettingsDto.class)
            )
    )
    public ResponseEntity<BrowserLanguageSettingsDto> get(
            @Parameter(hidden = true) Authentication authentication
    ) {
        return ResponseEntity.ok(
                browserLanguageSettingsService.getByGoogleSubject(authentication.getName())
        );
    }

    @PatchMapping
    @Operation(
            operationId = "updateBrowserLanguageSettings",
            summary = "Update the source and definition languages for future work"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated language settings",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BrowserLanguageSettingsDto.class)
            )
    )
    public ResponseEntity<BrowserLanguageSettingsDto> update(
            @Parameter(hidden = true) Authentication authentication,
            @Valid @RequestBody UpdateLanguageSettingsRequestDto request
    ) {
        return ResponseEntity.ok(browserLanguageSettingsService.updateByGoogleSubject(
                authentication.getName(),
                request.sourceLanguage(),
                request.definitionLanguage()
        ));
    }
}
