package com.example.whatsanki.controller;

import com.example.whatsanki.service.security.BrowserReturnPath;
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

@RestController
@RequestMapping("/api/v1/auth")
public class BrowserAuthenticationController {

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
        session.setAttribute(BrowserReturnPath.SESSION_ATTRIBUTE, BrowserReturnPath.validate(returnTo));
        String location = "/oauth2/authorization/google" + (reauthenticate ? "?prompt=login" : "");
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(location).toString())
                .build();
    }
}
