package com.example.whatsanki.controller;

import com.example.whatsanki.dto.BrowserDeckDto;
import com.example.whatsanki.dto.BrowserDeckListDto;
import com.example.whatsanki.dto.BrowserExportDto;
import com.example.whatsanki.dto.BrowserExportListDto;
import com.example.whatsanki.dto.DeckMutationRequestDto;
import com.example.whatsanki.service.BrowserDeckService;
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
import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
@ExternalEndpointSecurity(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF_OWNER_SCOPED)
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class BrowserDeckController {
    private final BrowserDeckService browserDeckService;

    @GetMapping("/decks")
    @Operation(operationId = "listBrowserDecks", summary = "List active owned decks")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = BrowserDeckListDto.class)))
    public ResponseEntity<BrowserDeckListDto> list(
            Authentication authentication,
            @RequestParam(required = false) @Size(max = 256) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize
    ) {
        return ResponseEntity.ok(browserDeckService.list(authentication.getName(), search, page, pageSize));
    }

    @PostMapping("/decks")
    @Operation(operationId = "createBrowserDeck", summary = "Create a deck")
    @ApiResponse(responseCode = "201", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = BrowserDeckDto.class)))
    public ResponseEntity<BrowserDeckDto> create(
            Authentication authentication, @Valid @RequestBody DeckMutationRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(browserDeckService.create(authentication.getName(), request));
    }

    @GetMapping("/decks/{reference}")
    @Operation(operationId = "getBrowserDeck", summary = "Get an owned deck")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = BrowserDeckDto.class)))
    public ResponseEntity<BrowserDeckDto> get(
            Authentication authentication, @PathVariable String reference
    ) {
        return ResponseEntity.ok(browserDeckService.get(authentication.getName(), reference));
    }

    @PatchMapping("/decks/{reference}")
    @Operation(operationId = "updateBrowserDeck", summary = "Rename or recolor a deck")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = BrowserDeckDto.class)))
    public ResponseEntity<BrowserDeckDto> update(
            Authentication authentication,
            @PathVariable String reference,
            @Valid @RequestBody DeckMutationRequestDto request
    ) {
        return ResponseEntity.ok(browserDeckService.update(authentication.getName(), reference, request));
    }

    @DeleteMapping("/decks/{reference}")
    @Operation(operationId = "deleteBrowserDeck", summary = "Delete an unreferenced deck")
    public ResponseEntity<Void> delete(
            Authentication authentication, @PathVariable String reference
    ) {
        browserDeckService.delete(authentication.getName(), reference);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/decks/{reference}/exports")
    @Operation(operationId = "createBrowserDeckExport", summary = "Create an immutable deck export")
    @ApiResponse(responseCode = "202", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = BrowserExportDto.class)))
    public ResponseEntity<BrowserExportDto> export(
            Authentication authentication, @PathVariable String reference
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(browserDeckService.export(authentication.getName(), reference));
    }

    @GetMapping("/exports")
    @Operation(operationId = "listBrowserExports", summary = "List recent owned exports")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = BrowserExportListDto.class)))
    public ResponseEntity<BrowserExportListDto> exports(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize
    ) {
        return ResponseEntity.ok(browserDeckService.exports(authentication.getName(), page, pageSize));
    }

    @GetMapping("/exports/{reference}")
    @Operation(operationId = "getBrowserExport", summary = "Get owned export state")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = BrowserExportDto.class)))
    public ResponseEntity<BrowserExportDto> exportStatus(
            Authentication authentication, @PathVariable String reference
    ) {
        return ResponseEntity.ok(browserDeckService.exportStatus(authentication.getName(), reference));
    }

    @GetMapping("/exports/{reference}/download")
    @Operation(operationId = "downloadBrowserExport", summary = "Authorize an expiring export download")
    public ResponseEntity<Void> download(
            Authentication authentication, @PathVariable String reference
    ) {
        URI location = browserDeckService.download(authentication.getName(), reference);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .build();
    }
}
