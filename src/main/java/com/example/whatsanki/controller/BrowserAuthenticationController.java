package com.example.whatsanki.controller;

import com.example.whatsanki.service.security.BrowserReturnPath;
import com.example.whatsanki.service.demo.DemoWorkspaceService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Starts browser OAuth after validating the return path and clearing anonymous demo state. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class BrowserAuthenticationController {

    private final DemoWorkspaceService demoWorkspaceService;

    @GetMapping("/google")
    @Operation(
            operationId = "startGoogleLogin",
            summary = "Start Google sign-in with a validated frontend return path"
    )
    @ApiResponse(responseCode = "302", description = "Redirect to Spring Security's Google authorization endpoint")
    public ResponseEntity<Void> google(
            @RequestParam(required = false) String returnTo,
            @RequestParam(defaultValue = "false") boolean reauthenticate,
            HttpSession session
    ) {
        // A demo session must not survive the transition into an authenticated account.
        demoWorkspaceService.cleanupForSignIn(session);
        session.setAttribute(BrowserReturnPath.SESSION_ATTRIBUTE, BrowserReturnPath.validate(returnTo));
        String location = "/oauth2/authorization/google" + (reauthenticate ? "?prompt=login" : "");
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(location).toString())
                .build();
    }
}
