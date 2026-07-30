package com.example.whatsanki.controller;

import com.example.whatsanki.dto.BrowserDeckDto;
import com.example.whatsanki.dto.BrowserExportDto;
import com.example.whatsanki.dto.DeckMutationRequestDto;
import com.example.whatsanki.service.BrowserDeckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BrowserDeckController {
    private final BrowserDeckService browserDeckService;

    @GetMapping("/decks")
    @Operation(operationId = "listBrowserDecks", summary = "List active owned decks")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = BrowserDeckDto.class))))
    public ResponseEntity<List<BrowserDeckDto>> list(Authentication authentication) {
        return ResponseEntity.ok(browserDeckService.list(authentication.getName()));
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
            array = @ArraySchema(schema = @Schema(implementation = BrowserExportDto.class))))
    public ResponseEntity<List<BrowserExportDto>> exports(Authentication authentication) {
        return ResponseEntity.ok(browserDeckService.exports(authentication.getName()));
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
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
