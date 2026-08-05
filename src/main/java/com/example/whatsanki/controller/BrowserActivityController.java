package com.example.whatsanki.controller;

import com.example.whatsanki.dto.ActivityItemDto;
import com.example.whatsanki.dto.ActivityPageDto;
import com.example.whatsanki.dto.ActivityStatusQueryRequestDto;
import com.example.whatsanki.dto.ActivityStatusQueryResponseDto;
import com.example.whatsanki.service.BrowserActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class BrowserActivityController {
    private final BrowserActivityService browserActivityService;

    @GetMapping
    @Operation(operationId = "listBrowserActivity", summary = "List logical activity for the authenticated user")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ActivityPageDto.class)))
    public ResponseEntity<ActivityPageDto> list(
            Authentication authentication,
            @RequestParam(required = false) ActivityItemDto.Category category,
            @RequestParam(required = false) ActivityItemDto.Lifecycle lifecycle,
            @RequestParam(required = false) @Size(max = 160) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(browserActivityService.list(authentication.getName(), category, lifecycle, cursor, limit));
    }

    @PostMapping("/status-query")
    @Operation(operationId = "queryBrowserActivityStatuses", summary = "Batch query owned activity statuses")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ActivityStatusQueryResponseDto.class)))
    public ResponseEntity<ActivityStatusQueryResponseDto> statuses(
            Authentication authentication,
            @Valid @RequestBody ActivityStatusQueryRequestDto request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(browserActivityService.queryStatuses(authentication.getName(), request.references()));
    }
}
