package com.example.whatsanki.controller;

import com.example.whatsanki.dto.ApiErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
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
}
