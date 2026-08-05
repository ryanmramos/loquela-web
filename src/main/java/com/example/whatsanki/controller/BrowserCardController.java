package com.example.whatsanki.controller;

import com.example.whatsanki.dto.BrowserCardDto;
import com.example.whatsanki.dto.BrowserCardListDto;
import com.example.whatsanki.dto.CardMutationRequestDto;
import com.example.whatsanki.service.BrowserCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ExternalEndpointSecurity(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF_OWNER_SCOPED)
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Validated
public class BrowserCardController {
    private final BrowserCardService browserCardService;

    @GetMapping
    @Operation(operationId = "listBrowserCards", summary = "List owned cards")
    @ApiResponse(
            responseCode = "200",
            description = "Card page",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BrowserCardListDto.class)
            )
    )
    public ResponseEntity<BrowserCardListDto> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String search,
            @RequestParam(required = false) String deckReference
    ) {
        return ResponseEntity.ok(browserCardService.list(
                authentication.getName(), page, pageSize, search, deckReference
        ));
    }

    @PostMapping
    @Operation(operationId = "createBrowserCard", summary = "Create a card")
    @ApiResponse(
            responseCode = "201",
            description = "Created card",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BrowserCardDto.class)
            )
    )
    public ResponseEntity<BrowserCardDto> create(
            Authentication authentication,
            @Valid @RequestBody CardMutationRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(browserCardService.create(authentication.getName(), request));
    }

    @GetMapping("/{reference}")
    @Operation(operationId = "getBrowserCard", summary = "Get an owned card")
    @ApiResponse(
            responseCode = "200",
            description = "Card",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BrowserCardDto.class)
            )
    )
    public ResponseEntity<BrowserCardDto> get(
            Authentication authentication,
            @PathVariable String reference
    ) {
        return ResponseEntity.ok(browserCardService.get(authentication.getName(), reference));
    }

    @PatchMapping("/{reference}")
    @Operation(operationId = "updateBrowserCard", summary = "Update an owned card")
    @ApiResponse(
            responseCode = "200",
            description = "Updated card",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BrowserCardDto.class)
            )
    )
    public ResponseEntity<BrowserCardDto> update(
            Authentication authentication,
            @PathVariable String reference,
            @Valid @RequestBody CardMutationRequestDto request
    ) {
        return ResponseEntity.ok(browserCardService.update(authentication.getName(), reference, request));
    }

    @DeleteMapping("/{reference}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable String reference
    ) {
        browserCardService.delete(authentication.getName(), reference);
        return ResponseEntity.noContent().build();
    }
}
