package com.example.whatsanki.controller;

import com.example.whatsanki.dto.AcceptedSourceDto;
import com.example.whatsanki.dto.BrowserSourceDetailsDto;
import com.example.whatsanki.dto.BrowserSourceSummaryDto;
import com.example.whatsanki.dto.CreatePastedTextSourceRequestDto;
import com.example.whatsanki.dto.CreateYouTubeSourceRequestDto;
import com.example.whatsanki.dto.PastedTextSourceDto;
import com.example.whatsanki.dto.RenameSourceRequestDto;
import com.example.whatsanki.dto.SourceListDto;
import com.example.whatsanki.dto.SourceMutationDto;
import com.example.whatsanki.service.BrowserSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
@Validated
public class BrowserSourceController {

    private final BrowserSourceService browserSourceService;

    @PostMapping
    @Operation(operationId = "createPastedTextSource", summary = "Create a pasted-text source")
    @ApiResponse(
            responseCode = "202",
            description = "Source accepted for indexing",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PastedTextSourceDto.class)
            )
    )
    public ResponseEntity<PastedTextSourceDto> createPastedText(
            @Parameter(hidden = true) Authentication authentication,
            @Valid @RequestBody CreatePastedTextSourceRequestDto requestDto
    ) {
        return ResponseEntity.accepted().body(
                browserSourceService.createPastedText(authentication.getName(), requestDto.text())
        );
    }

    @PostMapping("/youtube")
    @Operation(operationId = "createYouTubeSource", summary = "Create a YouTube subtitle source")
    @ApiResponse(
            responseCode = "202",
            description = "Source accepted",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AcceptedSourceDto.class)
            )
    )
    public ResponseEntity<AcceptedSourceDto> createYouTube(
            @Parameter(hidden = true) Authentication authentication,
            @Valid @RequestBody CreateYouTubeSourceRequestDto requestDto
    ) {
        return ResponseEntity.accepted().body(
                browserSourceService.createYouTube(authentication.getName(), requestDto.url())
        );
    }

    @GetMapping
    @Operation(operationId = "listSources", summary = "List the authenticated user's sources")
    @ApiResponse(
            responseCode = "200",
            description = "Source page",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SourceListDto.class)
            )
    )
    public ResponseEntity<SourceListDto> list(
            @Parameter(hidden = true) Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String search,
            @RequestParam(required = false) BrowserSourceService.SourceKind kind,
            @RequestParam(required = false) BrowserSourceService.SourceStatus status,
            @RequestParam(defaultValue = "CREATED_AT") BrowserSourceService.SortField sort,
            @RequestParam(defaultValue = "DESC") BrowserSourceService.SortDirection direction
    ) {
        return ResponseEntity.ok(browserSourceService.list(
                authentication.getName(), page, pageSize, search, kind, status, sort, direction
        ));
    }

    @GetMapping("/{sourceReference}")
    @Operation(operationId = "getSource", summary = "Get an owned source")
    @ApiResponse(
            responseCode = "200",
            description = "Source details",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = BrowserSourceDetailsDto.class)
            )
    )
    public ResponseEntity<BrowserSourceDetailsDto> get(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable String sourceReference
    ) {
        return ResponseEntity.ok(browserSourceService.get(authentication.getName(), sourceReference));
    }

    @PatchMapping("/{sourceReference}")
    @Operation(operationId = "renameSource", summary = "Rename an owned source")
    @ApiResponse(
            responseCode = "200",
            description = "Renamed source",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = BrowserSourceSummaryDto.class)
            )
    )
    public ResponseEntity<BrowserSourceSummaryDto> rename(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable String sourceReference,
            @Valid @RequestBody RenameSourceRequestDto requestDto
    ) {
        return ResponseEntity.ok(browserSourceService.rename(
                authentication.getName(),
                sourceReference,
                requestDto.displayName(),
                requestDto.version()
        ));
    }

    @PostMapping("/{sourceReference}/retry")
    @Operation(operationId = "retrySource", summary = "Retry a backend-declared retryable source")
    @ApiResponse(
            responseCode = "202",
            description = "Retry accepted",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SourceMutationDto.class)
            )
    )
    public ResponseEntity<SourceMutationDto> retry(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable String sourceReference
    ) {
        return ResponseEntity.accepted().body(
                browserSourceService.retry(authentication.getName(), sourceReference)
        );
    }

    @DeleteMapping("/{sourceReference}")
    @Operation(operationId = "deleteSource", summary = "Queue deletion of an owned source")
    @ApiResponse(
            responseCode = "202",
            description = "Deletion accepted",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SourceMutationDto.class)
            )
    )
    public ResponseEntity<SourceMutationDto> delete(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable String sourceReference
    ) {
        return ResponseEntity.accepted().body(
                browserSourceService.delete(authentication.getName(), sourceReference)
        );
    }
}
