package com.example.whatsanki.controller;

import com.example.whatsanki.dto.CreatePastedTextSourceRequestDto;
import com.example.whatsanki.dto.PastedTextSourceDto;
import com.example.whatsanki.service.BrowserSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
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
}
