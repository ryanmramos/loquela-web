package com.example.whatsanki.controller;

import com.example.whatsanki.dto.ApiErrorResponseDto;
import com.example.whatsanki.dto.ApiProblemDto;
import com.example.whatsanki.service.exception.BrowserAccountUnavailableException;
import com.example.whatsanki.service.exception.AdminAccessException;
import com.example.whatsanki.service.exception.BrowserDeckException;
import com.example.whatsanki.service.exception.BrowserPracticeException;
import com.example.whatsanki.service.exception.BrowserPrivacyException;
import com.example.whatsanki.service.exception.BrowserActivityException;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.exception.BrowserSourceNotFoundException;
import com.example.whatsanki.service.exception.BrowserSourceValidationException;
import com.example.whatsanki.service.exception.UnsupportedBrowserLanguagePairException;
import com.example.whatsanki.service.exception.DemoException;
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

    @ExceptionHandler(DemoException.class)
    public ResponseEntity<ApiProblemDto> handleDemo(DemoException exception, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(org.springframework.http.CacheControl.noStore());
        if (exception.getRetryAfterSeconds() != null) {
            response.header(org.springframework.http.HttpHeaders.RETRY_AFTER,
                    exception.getRetryAfterSeconds().toString());
        }
        return response.body(new ApiProblemDto(
                URI.create("https://loquela.app/problems/" + exception.getProblemCode()
                        .toLowerCase().replace('_', '-')),
                "Demo operation could not be completed",
                exception.getStatus().value(),
                exception.getProblemCode(),
                demoProblemDetail(exception.getProblemCode()),
                request.getRequestURI(), MDC.get("correlationId"), List.of()
        ));
    }

    @ExceptionHandler(AdminAccessException.class)
    public ResponseEntity<ApiProblemDto> handleAdminAccess(
            AdminAccessException exception,
            HttpServletRequest request
    ) {
        String detail = switch (exception.problemCode()) {
            case "RECENT_AUTHENTICATION_REQUIRED" ->
                    "Sign in with Google again before changing administrator access.";
            case "ADMIN_CONFIRMATION_REQUIRED" ->
                    "Confirm the exact target email before changing administrator access.";
            case "LAST_ADMIN_REQUIRED" -> "At least one active administrator is required.";
            case "SELF_ADMIN_CHANGE_FORBIDDEN" ->
                    "Another administrator must change your administrator access.";
            default -> "The administrative access change could not be completed.";
        };
        return problem(
                exception.status(),
                exception.problemCode(),
                "Administrative access operation rejected",
                detail,
                request
        );
    }

    @ExceptionHandler(BrowserActivityException.class)
    public ResponseEntity<ApiProblemDto> handleActivity(
            BrowserActivityException exception,
            HttpServletRequest request
    ) {
        return problem(
                exception.status(),
                exception.problemCode(),
                "Activity could not be loaded",
                "Reset the activity filters and try again.",
                request
        );
    }

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

    @ExceptionHandler(BrowserPrivacyException.class)
    public ResponseEntity<ApiProblemDto> handlePrivacy(
            BrowserPrivacyException exception,
            HttpServletRequest request
    ) {
        return problem(
                exception.status(),
                exception.problemCode(),
                "Privacy request could not be completed",
                exception.problemCode().equals("RECENT_AUTHENTICATION_REQUIRED")
                        ? "Sign in with Google again before deleting the account."
                        : "Review the confirmation and try again.",
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
            case "YOUTUBE_INGESTION_DISABLED" ->
                    "YouTube ingestion is temporarily unavailable. Try again later.";
            default -> "Review the source and try again.";
        };
    }

    private String demoProblemDetail(String code) {
        return switch (code) {
            case "DEMO_UNAVAILABLE" -> "The temporary demo is not available right now.";
            case "DEMO_INGESTION_DISABLED" -> "Source ingestion is temporarily paused; lookup and deck features remain available.";
            case "DEMO_EXPIRED" -> "This temporary demo workspace expired or is no longer available.";
            case "DEMO_BUSY" -> "The demo is at capacity. Wait briefly and try again.";
            case "DEMO_RATE_LIMITED" -> "The demo request limit was reached. Wait and try again.";
            case "DEMO_DECK_FULL" -> "Loquela Demo can contain at most 20 cards.";
            case "DEMO_DECK_EMPTY" -> "Add at least one card before downloading the deck.";
            case "PDF_NEEDS_OCR" -> "This PDF has no readable text layer. Run OCR and try again.";
            case "PDF_TOO_LARGE", "PDF_TOO_MANY_PAGES", "PDF_TEXT_TOO_LARGE",
                    "PASTED_TEXT_TOO_LARGE", "DEMO_TOTAL_TEXT_LIMIT", "YOUTUBE_TOO_LONG" ->
                    "Choose a source within the limits shown on the demo page.";
            default -> "Review the demo page and try again.";
        };
    }
}
