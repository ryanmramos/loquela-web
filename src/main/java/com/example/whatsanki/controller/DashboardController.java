package com.example.whatsanki.controller;

import com.example.whatsanki.dto.DashboardDto;
import com.example.whatsanki.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(operationId = "getDashboard", summary = "Return the authenticated dashboard aggregate")
    @ApiResponse(
            responseCode = "200",
            description = "Dashboard aggregate",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = DashboardDto.class)
            )
    )
    public ResponseEntity<DashboardDto> get(
            @Parameter(hidden = true) Authentication authentication
    ) {
        return ResponseEntity.ok(dashboardService.getByGoogleSubject(authentication.getName()));
    }
}
