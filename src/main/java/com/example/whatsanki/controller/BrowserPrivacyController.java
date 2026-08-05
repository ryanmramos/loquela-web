package com.example.whatsanki.controller;

import com.example.whatsanki.dto.BrowserAccountDeletionDto;
import com.example.whatsanki.dto.BrowserAccountDeletionRequestDto;
import com.example.whatsanki.dto.BrowserAccountDeletionStatusDto;
import com.example.whatsanki.service.BrowserPrivacyService;
import com.example.whatsanki.service.exception.BrowserPrivacyException;
import com.example.whatsanki.service.security.BrowserAuthenticationSuccessHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/account-deletion-requests")
@RequiredArgsConstructor
public class BrowserPrivacyController {
    private static final Duration RECENT_AUTHENTICATION_LIFETIME = Duration.ofMinutes(15);

    private final BrowserPrivacyService browserPrivacyService;

    @PostMapping
    @Operation(operationId = "createBrowserAccountDeletion", summary = "Queue recently reauthenticated account deletion")
    public ResponseEntity<BrowserAccountDeletionDto> create(
            Authentication authentication,
            HttpSession session,
            @Valid @RequestBody BrowserAccountDeletionRequestDto request
    ) {
        requireRecentAuthentication(session);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(browserPrivacyService.deleteAccount(authentication.getName()));
    }

    @GetMapping("/current")
    @Operation(operationId = "getCurrentBrowserAccountDeletion", summary = "Read current account deletion state")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = BrowserAccountDeletionStatusDto.class)))
    public ResponseEntity<BrowserAccountDeletionStatusDto> current(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(browserPrivacyService.currentDeletion(authentication.getName()));
    }

    private void requireRecentAuthentication(HttpSession session) {
        Object value = session.getAttribute(BrowserAuthenticationSuccessHandler.RECENT_AUTHENTICATION_AT);
        if (!(value instanceof Long epochMillis)
                || Instant.ofEpochMilli(epochMillis).plus(RECENT_AUTHENTICATION_LIFETIME).isBefore(Instant.now())) {
            throw new BrowserPrivacyException(HttpStatus.FORBIDDEN, "RECENT_AUTHENTICATION_REQUIRED");
        }
    }
}
