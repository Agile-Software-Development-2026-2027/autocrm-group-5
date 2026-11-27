package it.devmethodologies.meetingingestor.controller;

import it.devmethodologies.meetingingestor.model.keypoint.ExtractionRequest;
import it.devmethodologies.meetingingestor.model.keypoint.ExtractionResponse;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.ExtractionRateLimitedException;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.ExtractionRejectedException;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.KeyPointsUnavailableException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public extraction endpoint (docs/api-contract.md, section 5): the frontend calls it, the backend validates,
 * forwards to keypoints-services with the internal API key and maps its answers.
 * Every error body is {"error": "...", "code": "..."}.
 */
@RestController
@RequestMapping("/api/extractions")
public class ExtractionController {

    private static final Logger log = LoggerFactory.getLogger(ExtractionController.class);

    private final KeyPointsService keyPointsService;

    public ExtractionController(KeyPointsService keyPointsService) {
        this.keyPointsService = keyPointsService;
    }

    @PostMapping
    public ExtractionResponse extract(@Valid @RequestBody ExtractionRequest request) {
        return keyPointsService.extract(request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage()).findFirst().orElse("The request is not valid.");
        return error(HttpStatus.BAD_REQUEST, message, "invalid_request");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable() {
        return error(HttpStatus.BAD_REQUEST, "The request body is not valid JSON.", "invalid_request");
    }

    @ExceptionHandler(ExtractionRejectedException.class)
    public ResponseEntity<Map<String, String>> rejected(ExtractionRejectedException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), "invalid_request");
    }

    @ExceptionHandler(ExtractionRateLimitedException.class)
    public ResponseEntity<Map<String, String>> rateLimited(ExtractionRateLimitedException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (e.getRetryAfter() != null) {
            response.header(HttpHeaders.RETRY_AFTER, e.getRetryAfter());
        }
        return response.body(Map.of("error", "Too many extractions, try again later.", "code", "rate_limited"));
    }

    @ExceptionHandler(KeyPointsUnavailableException.class)
    public ResponseEntity<Map<String, String>> unavailable(KeyPointsUnavailableException e) {
        // Details only in the logs; the caller gets a generic message.
        log.error("keypoints-services unavailable", e);
        return error(HttpStatus.BAD_GATEWAY, "The extraction service is not available.", "extraction_unavailable");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message, String code) {
        return ResponseEntity.status(status).body(Map.of("error", message, "code", code));
    }
}
