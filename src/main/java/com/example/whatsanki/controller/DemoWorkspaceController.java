package com.example.whatsanki.controller;

import com.example.whatsanki.dto.demo.DemoDtos;
import com.example.whatsanki.service.demo.DemoWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
@Validated
@ExternalEndpointSecurity(ExternalEndpointSecurity.Control.ANONYMOUS_DEMO_SESSION_CSRF)
public class DemoWorkspaceController {
    private static final CacheControl NO_STORE = CacheControl.noStore();
    private static final String REFERENCE = "[0-9a-fA-F-]{36}";
    private final DemoWorkspaceService demoWorkspaceService;

    @GetMapping("/capabilities")
    @Operation(operationId = "getDemoCapabilities")
    public ResponseEntity<DemoDtos.Capabilities> capabilities() {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.capabilities());
    }

    @PostMapping("/workspace")
    @Operation(operationId = "startDemoWorkspace")
    public ResponseEntity<DemoDtos.WorkspaceView> start(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.start(request));
    }

    @GetMapping("/workspace")
    @Operation(operationId = "getDemoWorkspace")
    public ResponseEntity<DemoDtos.WorkspaceView> workspace(HttpSession session) {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.get(session));
    }

    @PostMapping("/workspace/reset")
    @Operation(operationId = "resetDemoWorkspace")
    public ResponseEntity<DemoDtos.WorkspaceView> reset(HttpSession session) {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.reset(session));
    }

    @PostMapping("/workspace/exit")
    @Operation(operationId = "exitDemoWorkspace")
    public ResponseEntity<Void> exit(HttpSession session) {
        demoWorkspaceService.exit(session);
        return ResponseEntity.noContent().cacheControl(NO_STORE).build();
    }

    @PutMapping("/languages")
    @Operation(operationId = "updateDemoLanguages")
    public ResponseEntity<DemoDtos.WorkspaceView> languages(
            HttpSession session, @Valid @RequestBody DemoDtos.LanguagesRequest request) {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.languages(session, request));
    }

    @PostMapping("/sources/pasted-text")
    @Operation(operationId = "createDemoPastedTextSource")
    public ResponseEntity<DemoDtos.WorkspaceView> pastedText(HttpSession session,
            @Valid @RequestBody DemoDtos.PastedTextRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.accepted().cacheControl(NO_STORE)
                .body(demoWorkspaceService.ingestPastedText(session, request, servletRequest));
    }

    @PostMapping(path = "/sources/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "createDemoPdfSource")
    public ResponseEntity<DemoDtos.WorkspaceView> pdf(HttpSession session,
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return ResponseEntity.accepted().cacheControl(NO_STORE)
                .body(demoWorkspaceService.ingestPdf(session, file, request));
    }

    @PostMapping(path = "/sources/timed-transcript", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "createDemoTimedTranscriptSource")
    public ResponseEntity<DemoDtos.WorkspaceView> timedTranscript(HttpSession session,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String originUrl,
            @RequestParam(defaultValue = "0") long timelineOffsetMs,
            HttpServletRequest request) {
        return ResponseEntity.accepted().cacheControl(NO_STORE)
                .body(demoWorkspaceService.ingestTimedTranscript(session, file, originUrl, timelineOffsetMs, request));
    }

    @PostMapping("/sources/{source-type}/retry")
    @Operation(operationId = "retryDemoSource")
    public ResponseEntity<DemoDtos.WorkspaceView> retry(HttpSession session,
            @PathVariable("source-type") DemoDtos.SourceType type, HttpServletRequest request) {
        return ResponseEntity.accepted().cacheControl(NO_STORE)
                .body(demoWorkspaceService.retrySource(session, type, request));
    }

    @DeleteMapping("/sources/{source-type}")
    @Operation(operationId = "deleteDemoSource")
    public ResponseEntity<DemoDtos.WorkspaceView> deleteSource(HttpSession session,
            @PathVariable("source-type") DemoDtos.SourceType type) {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.removeSource(session, type));
    }

    @PostMapping("/lookups")
    @Operation(operationId = "createDemoLookup")
    public ResponseEntity<DemoDtos.LookupView> lookup(HttpSession session,
            @Valid @RequestBody DemoDtos.LookupRequest request) {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.lookup(session, request));
    }

    @PostMapping("/cards")
    @Operation(operationId = "createDemoCard")
    public ResponseEntity<DemoDtos.CardMutation> card(HttpSession session,
            @Valid @RequestBody DemoDtos.SaveCardRequest request) {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.saveCard(session, request));
    }

    @DeleteMapping("/cards/{card-reference}")
    @Operation(operationId = "deleteDemoCard")
    public ResponseEntity<DemoDtos.CardRemoval> deleteCard(HttpSession session,
            @PathVariable("card-reference") @Pattern(regexp = REFERENCE) String reference) {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.removeCard(session, reference));
    }

    @PostMapping("/cards/{card-reference}/undo")
    @Operation(operationId = "undoDemoCardDeletion")
    public ResponseEntity<DemoDtos.CardMutation> undoCard(HttpSession session,
            @PathVariable("card-reference") @Pattern(regexp = REFERENCE) String reference,
            @RequestParam @Pattern(regexp = REFERENCE) String token) {
        return ResponseEntity.ok().cacheControl(NO_STORE)
                .body(demoWorkspaceService.undoCard(session, reference, token));
    }

    @PostMapping("/exports")
    @Operation(operationId = "createDemoExport")
    public ResponseEntity<DemoDtos.ExportView> export(HttpSession session, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(NO_STORE).body(demoWorkspaceService.export(session, request));
    }

    @PostMapping("/exports/download")
    @Operation(operationId = "createAndDownloadDemoExport")
    public ResponseEntity<org.springframework.core.io.Resource> exportDownload(
            HttpSession session, HttpServletRequest request) {
        DemoDtos.ExportView export = demoWorkspaceService.export(session, request);
        return downloadResponse(demoWorkspaceService.download(session, export.reference()));
    }

    @GetMapping("/exports/{export-reference}/download")
    @Operation(operationId = "downloadDemoExport")
    public ResponseEntity<org.springframework.core.io.Resource> download(HttpSession session,
            @PathVariable("export-reference") @Pattern(regexp = REFERENCE) String reference) {
        return downloadResponse(demoWorkspaceService.download(session, reference));
    }

    private ResponseEntity<org.springframework.core.io.Resource> downloadResponse(
            DemoWorkspaceService.DemoDownload download) {
        return ResponseEntity.ok().cacheControl(NO_STORE)
                .contentType(MediaType.parseMediaType("application/vnd.anki"))
                .contentLength(download.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"loquela-demo.apkg\"")
                .body(download.resource());
    }
}
