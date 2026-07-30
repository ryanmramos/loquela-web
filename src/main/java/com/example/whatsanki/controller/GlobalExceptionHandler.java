package com.example.whatsanki.controller;

import com.example.whatsanki.dto.ApiErrorResponseDto;
import com.example.whatsanki.dto.ApiProblemDto;
import com.example.whatsanki.service.exception.BrowserAccountUnavailableException;
import com.example.whatsanki.service.exception.BrowserDeckException;
import com.example.whatsanki.service.exception.BrowserPracticeException;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.exception.BrowserSourceNotFoundException;
import com.example.whatsanki.service.exception.BrowserSourceValidationException;
import com.example.whatsanki.service.exception.UnsupportedBrowserLanguagePairException;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.net.URI;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BrowserPracticeException.class)
    public ResponseEntity<ApiProblemDto> handlePractice(
            BrowserPracticeException exception,
            HttpServletRequest request
    ) {
        return problem(
                exception.getStatus(),
                exception.getProblemCode(),
                "Practice session could not be completed",
                "Refresh the practice page or start a new session.",
                request
        );
    }

    @ExceptionHandler(BrowserDeckException.class)
    public ResponseEntity<ApiProblemDto> handleDeck(
            BrowserDeckException exception,
            HttpServletRequest request
    ) {
        return problem(
                exception.status(),
                exception.problemCode(),
                "Deck operation could not be completed",
                "Review the deck and try again.",
                request
        );
    }

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

    @ExceptionHandler(BrowserSourceNotFoundException.class)
    public ResponseEntity<ApiProblemDto> handleSourceNotFound(
            BrowserSourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                "SOURCE_NOT_FOUND",
                "Source not found",
                "The source is unavailable.",
                request
        );
    }

    @ExceptionHandler(BrowserSourceConflictException.class)
    public ResponseEntity<ApiProblemDto> handleSourceConflict(
            BrowserSourceConflictException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.CONFLICT,
                exception.problemCode(),
                "Source operation could not be completed",
                sourceProblemDetail(exception.problemCode()),
                request
        );
    }

    @ExceptionHandler(BrowserSourceValidationException.class)
    public ResponseEntity<ApiProblemDto> handleSourceValidation(
            BrowserSourceValidationException exception,
            HttpServletRequest request
    ) {
        return problem(
                exception.status(),
                exception.problemCode(),
                "Source could not be accepted",
                sourceProblemDetail(exception.problemCode()),
                request
        );
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiProblemDto> handleParameterValidation(
            Exception exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                "One or more request parameters are invalid.",
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiProblemDto> handleParameterTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                "One or more request parameters are invalid.",
                request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiProblemDto> handleMultipartTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "DOCUMENT_TOO_LARGE",
                "Source could not be accepted",
                sourceProblemDetail("DOCUMENT_TOO_LARGE"),
                request
        );
    }

    @ExceptionHandler(UnsupportedBrowserLanguagePairException.class)
    public ResponseEntity<ApiProblemDto> handleUnsupportedBrowserLanguagePair(
            UnsupportedBrowserLanguagePairException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_LANGUAGE_PAIR",
                "Language pair is not supported",
                "Choose one of the supported source and definition language combinations.",
                request
        );
    }

    private ResponseEntity<ApiProblemDto> problem(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ApiProblemDto body = new ApiProblemDto(
                URI.create("https://loquela.app/problems/" + code.toLowerCase().replace('_', '-')),
                title,
                status.value(),
                code,
                detail,
                request.getRequestURI(),
                MDC.get("correlationId"),
                List.of()
        );
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private String sourceProblemDetail(String code) {
        return switch (code) {
            case "DOCUMENT_TOO_LARGE" -> "Choose a document within the displayed size limit.";
            case "UNSUPPORTED_MEDIA_TYPE" -> "Choose a PDF, UTF-8 TXT, or EPUB document.";
            case "ENCRYPTED_DOCUMENT" -> "Remove document encryption and try again.";
            case "PDF_NEEDS_OCR" -> "This PDF has no readable text layer. Run OCR and try again.";
            case "CHECKSUM_MISMATCH", "UPLOAD_SIZE_MISMATCH" ->
                    "The selected file changed during upload. Choose it again.";
            case "MEDIA_TYPE_MISMATCH" -> "The file contents do not match its declared type.";
            case "INVALID_TEXT_ENCODING" -> "Save the text document as UTF-8 and try again.";
            case "UPLOAD_ALREADY_COMPLETED" -> "This upload was already finalized.";
            case "UPLOAD_EXPIRED" -> "The upload expired. Start a new upload.";
            case "SOURCE_VERSION_CONFLICT" -> "The source changed elsewhere. Refresh before renaming it.";
            case "SOURCE_NOT_RETRYABLE" -> "This failure cannot be retried with the original source data.";
            case "INVALID_YOUTUBE_URL" -> "Enter one HTTPS YouTube video URL. Playlists are not supported.";
            default -> "Review the source and try again.";
        };
    }
}
