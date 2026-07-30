package com.example.whatsanki.controller;

import com.example.whatsanki.dto.*;
import com.example.whatsanki.service.BrowserLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/lookups")
@RequiredArgsConstructor
@Validated
public class BrowserLookupController {
    private final BrowserLookupService browserLookupService;

    @PostMapping
    @Operation(operationId = "createBrowserLookup", summary = "Queue a contextual lookup")
    @ApiResponse(
            responseCode = "202",
            description = "Lookup accepted",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = BrowserLookupAcceptedDto.class)
            )
    )
    public ResponseEntity<BrowserLookupAcceptedDto> create(
            @Parameter(hidden = true) Authentication auth,
            @Valid @RequestBody CreateBrowserLookupRequestDto request
    ) {
        return ResponseEntity.accepted().body(browserLookupService.create(auth.getName(), request));
    }

    @GetMapping("/{lookupReference}")
    @Operation(operationId = "getBrowserLookup", summary = "Get an owned lookup snapshot")
    @ApiResponse(
            responseCode = "200",
            description = "Current lookup state",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = BrowserLookupDto.class)
            )
    )
    public ResponseEntity<BrowserLookupDto> get(
            @Parameter(hidden = true) Authentication auth,
            @PathVariable String lookupReference
    ) {
        return ResponseEntity.ok(browserLookupService.get(auth.getName(), lookupReference));
    }

    @GetMapping("/decks")
    @Operation(operationId = "listBrowserLookupDecks", summary = "List active decks available for lookup cards")
    @ApiResponse(
            responseCode = "200",
            description = "Active deck options",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = BrowserDeckOptionDto.class))
            )
    )
    public ResponseEntity<List<BrowserDeckOptionDto>> decks(
            @Parameter(hidden = true) Authentication auth
    ) {
        return ResponseEntity.ok(browserLookupService.decks(auth.getName()));
    }

    @PostMapping("/{lookupReference}/contexts/{contextNumber}/save")
    @Operation(operationId = "saveBrowserLookupContext", summary = "Save one lookup context as a card")
    @ApiResponse(
            responseCode = "200",
            description = "Saved card reference",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = BrowserLookupSaveDto.class)
            )
    )
    public ResponseEntity<BrowserLookupSaveDto> save(
            @Parameter(hidden = true) Authentication auth,
            @PathVariable String lookupReference,
            @PathVariable @Min(1) int contextNumber,
            @Valid @RequestBody SaveLookupResultRequestDto request
    ) {
        return ResponseEntity.ok(browserLookupService.save(
                auth.getName(), lookupReference, contextNumber, request.deckReference()
        ));
    }
}
