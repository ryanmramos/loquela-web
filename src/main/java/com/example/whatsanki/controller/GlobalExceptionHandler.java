package com.example.whatsanki.controller;

import com.example.whatsanki.dto.ApiErrorResponseDto;
import com.example.whatsanki.dto.ApiProblemDto;
import com.example.whatsanki.service.exception.BrowserAccountUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.net.URI;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponseDto> handleBinding(BindException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        log.warn(
                "Rejected request because DTO validation failed fields={}",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField())
                        .distinct()
                        .toList()
        );
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Twilio webhook request",
                details
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiProblemDto> handleRequestBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiProblemDto.FieldErrorDto> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiProblemDto.FieldErrorDto(
                        error.getField(),
                        "INVALID_FIELD",
                        error.getDefaultMessage() == null ? "The value is invalid." : error.getDefaultMessage()
                ))
                .toList();
        ApiProblemDto body = new ApiProblemDto(
                URI.create("https://loquela.app/problems/validation"),
                "Request validation failed",
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "One or more request fields are invalid.",
                request.getRequestURI(),
                MDC.get("correlationId"),
                fieldErrors
        );
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(BrowserAccountUnavailableException.class)
    public ResponseEntity<ApiProblemDto> handleBrowserAccountUnavailable(
            BrowserAccountUnavailableException exception,
            HttpServletRequest request
    ) {
        ApiProblemDto body = new ApiProblemDto(
                URI.create("https://loquela.app/problems/account-unavailable"),
                "Account unavailable",
                HttpStatus.FORBIDDEN.value(),
                exception.problemCode(),
                "This account cannot access the website.",
                request.getRequestURI(),
                MDC.get("correlationId"),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
