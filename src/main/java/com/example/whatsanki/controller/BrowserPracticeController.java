package com.example.whatsanki.controller;

import com.example.whatsanki.dto.CreatePracticeSessionRequestDto;
import com.example.whatsanki.dto.PracticeRatingRequestDto;
import com.example.whatsanki.dto.PracticeSessionDto;
import com.example.whatsanki.service.BrowserPracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ExternalEndpointSecurity(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF_OWNER_SCOPED)
@RestController
@RequestMapping("/api/v1/practice-sessions")
@RequiredArgsConstructor
public class BrowserPracticeController {
    private final BrowserPracticeService practiceService;

    @GetMapping("/active")
    @Operation(operationId = "getActivePracticeSession", summary = "Resume the active practice session")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PracticeSessionDto.class)))
    public ResponseEntity<PracticeSessionDto> active(Authentication authentication) {
        return practiceService.active(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @Operation(operationId = "createPracticeSession", summary = "Start or resume immutable deck practice")
    @ApiResponse(responseCode = "201", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PracticeSessionDto.class)))
    public ResponseEntity<PracticeSessionDto> start(
            Authentication authentication,
            @Valid @RequestBody CreatePracticeSessionRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(practiceService.start(authentication.getName(), request.deckReference()));
    }

    @GetMapping("/{reference}")
    @Operation(operationId = "getPracticeSession", summary = "Get authoritative practice position")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PracticeSessionDto.class)))
    public ResponseEntity<PracticeSessionDto> get(
            Authentication authentication, @PathVariable String reference
    ) {
        return ResponseEntity.ok(practiceService.get(authentication.getName(), reference));
    }

    @PostMapping("/{reference}/reveal")
    @Operation(operationId = "revealPracticeAnswer", summary = "Reveal the current practice answer")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PracticeSessionDto.class)))
    public ResponseEntity<PracticeSessionDto> reveal(
            Authentication authentication, @PathVariable String reference
    ) {
        return ResponseEntity.ok(practiceService.reveal(authentication.getName(), reference));
    }

    @PostMapping("/{reference}/ratings")
    @Operation(operationId = "ratePracticeCard", summary = "Record practice feedback and advance")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PracticeSessionDto.class)))
    public ResponseEntity<PracticeSessionDto> rate(
            Authentication authentication,
            @PathVariable String reference,
            @Valid @RequestBody PracticeRatingRequestDto request
    ) {
        return ResponseEntity.ok(
                practiceService.rate(authentication.getName(), reference, request)
        );
    }

    @DeleteMapping("/{reference}")
    @Operation(operationId = "endPracticeSession", summary = "End the active practice session")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PracticeSessionDto.class)))
    public ResponseEntity<PracticeSessionDto> end(
            Authentication authentication, @PathVariable String reference
    ) {
        return ResponseEntity.ok(practiceService.end(authentication.getName(), reference));
    }
}
