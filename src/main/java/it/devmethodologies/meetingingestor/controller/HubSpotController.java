package it.devmethodologies.meetingingestor.controller;

// SCRUM 67: Endpoint for HubSpot approval

import it.devmethodologies.meetingingestor.service.impl.HubSpotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/hubspot")
@CrossOrigin(origins = "*")
public class HubSpotController {

    private final HubSpotService hubSpotService;

    public HubSpotController(HubSpotService hubSpotService) {
        this.hubSpotService = hubSpotService;
    }

    // SCRUM 67: Create the backend endpoint to receive approved payloads
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncTranscript(@RequestBody SyncRequest request) {
        if (request.dealCode() == null || request.dealCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The deal code cannot be empty."));
        }
        if (request.text() == null || request.text().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The transcript text cannot be empty."));
        }

        try {
            HubSpotService.ApprovedNote created =
                    hubSpotService.publishApprovedTranscript(request.dealCode(), request.text());
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "dealId", created.dealId(),
                    "noteId", created.noteId()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record SyncRequest(String dealCode, String text) {}
}
