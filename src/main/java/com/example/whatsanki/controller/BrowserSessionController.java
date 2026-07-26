package com.example.whatsanki.controller;

import com.example.whatsanki.dto.ApiProblemDto;
import com.example.whatsanki.dto.BrowserSessionDto;
import com.example.whatsanki.service.BrowserSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
@RequiredArgsConstructor
public class BrowserSessionController {

    private final BrowserSessionService browserSessionService;

    @GetMapping
    @Operation(
            operationId = "getBrowserSession",
            summary = "Return the current authenticated browser session"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Authenticated session",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BrowserSessionDto.class)
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "No authenticated browser session",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ApiProblemDto.class)
            )
    )
    public ResponseEntity<BrowserSessionDto> current(
            @AuthenticationPrincipal OidcUser principal,
            @Parameter(hidden = true) CsrfToken csrfToken
    ) {
        csrfToken.getToken();
        return ResponseEntity.ok(browserSessionService.getByGoogleSubject(principal.getSubject()));
    }
}
