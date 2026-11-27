package it.devmethodologies.meetingingestor.controller;

import it.devmethodologies.meetingingestor.model.KeyPointsTranscript;
import it.devmethodologies.meetingingestor.model.KeyPointsTranscriptPage;
import it.devmethodologies.meetingingestor.model.keypoint.ExtractionRequest;
import it.devmethodologies.meetingingestor.model.keypoint.ExtractionResponse;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.KeyPointsUnavailableException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Walking skeleton of the backend -> keypoints-services integration:
 * GET /api/keypoints/transcripts         lists the transcripts (paginated, with text and key points)
 * GET /api/keypoints/transcripts/{id}    reads one transcript with its text and key points
 */
@RestController
@RequestMapping("/api/keypoints")
public class KeyPointsController {

    private static final Logger log = LoggerFactory.getLogger(KeyPointsController.class);

    private final KeyPointsService keyPointsService;

    public KeyPointsController(KeyPointsService keyPointsService) {
        this.keyPointsService = keyPointsService;
    }

    @GetMapping("/transcripts")
    public ResponseEntity<?> listTranscripts(@RequestParam(defaultValue = "1") int page) {
        if (page < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "The page must be 1 or higher."));
        }
        try {
            Optional<KeyPointsTranscriptPage> result = keyPointsService.listTranscripts(page);
            if (result.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Page not found."));
            }
            return ResponseEntity.ok(new ListResponse(result.get().count(), page, result.get().results()));
        } catch (KeyPointsUnavailableException e) {
            log.error("keypoints-services unavailable", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "The key points service is not available."));
        }
    }

    public record ListResponse(long count, int page, List<KeyPointsTranscript> results) {}

    @GetMapping("/transcripts/{id}")
    public ResponseEntity<?> getTranscript(@PathVariable long id) {
        if (id <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "The transcript id must be a positive number."));
        }
        try {
            Optional<KeyPointsTranscript> transcript = keyPointsService.getTranscript(id);
            if (transcript.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Transcript not found."));
            }
            return ResponseEntity.ok(transcript.get());
        } catch (KeyPointsUnavailableException e) {
            // Details only in the logs; the caller gets a generic message.
            log.error("keypoints-services unavailable", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "The key points service is not available."));
        }
    }


    @PostMapping("/extractions")
    public ResponseEntity<ExtractionResponse> extract(@Valid @RequestBody ExtractionRequest request) {
        return ResponseEntity.ok(keyPointsService.extract(request));
    }
}
