package com.example.whatsanki.controller;

import com.example.whatsanki.dto.AcceptedSourceDto;
import com.example.whatsanki.dto.CreateSourceUploadRequestDto;
import com.example.whatsanki.dto.SourceUploadIntentDto;
import com.example.whatsanki.service.BrowserSourceUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@ExternalEndpointSecurity(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF_OWNER_SCOPED)
@RestController
@RequestMapping("/api/v1/source-uploads")
@RequiredArgsConstructor
public class BrowserSourceUploadController {
    private final BrowserSourceUploadService browserSourceUploadService;

    @PostMapping
    @Operation(operationId = "createSourceUpload", summary = "Create a one-time document upload intent")
    @ApiResponse(
            responseCode = "200",
            description = "Upload intent",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SourceUploadIntentDto.class)
            )
    )
    public ResponseEntity<SourceUploadIntentDto> create(
            @Parameter(hidden = true) Authentication authentication,
            @Valid @RequestBody CreateSourceUploadRequestDto request
    ) {
        return ResponseEntity.ok(browserSourceUploadService.createIntent(authentication.getName(), request));
    }

    @PostMapping(path = "/{uploadReference}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "completeSourceUpload", summary = "Upload and finalize a document once")
    @ApiResponse(
            responseCode = "202",
            description = "Document accepted",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AcceptedSourceDto.class)
            )
    )
    public ResponseEntity<AcceptedSourceDto> complete(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable String uploadReference,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.accepted().body(
                browserSourceUploadService.complete(authentication.getName(), uploadReference, file)
        );
    }
}
