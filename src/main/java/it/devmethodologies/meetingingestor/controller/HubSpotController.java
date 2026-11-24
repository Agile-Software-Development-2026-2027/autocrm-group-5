package it.devmethodologies.meetingingestor.controller;

// SCRUM 67: Endpoint for HubSpot approval

import it.devmethodologies.meetingingestor.service.impl.HubSpotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hubspot")
@CrossOrigin(origins = "*")
public class HubSpotController {

    private final HubSpotService hubSpotService;

    public HubSpotController(HubSpotService hubSpotService) {
        this.hubSpotService = hubSpotService;
    }

    // SCRUM-101: Expose the list of HubSpot Deals for the frontend dropdown selector
    @GetMapping("/deals")
    public ResponseEntity<List<HubSpotService.HubSpotDeal>> getDeals() {
        try {
            return ResponseEntity.ok(hubSpotService.getDeals());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // SCRUM-67, SCRUM-101: Create the backend endpoint to receive approved payloads (single or multiple transcripts)
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncTranscript(@RequestBody SyncRequest request) {
        if (request.dealCode() == null || request.dealCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The deal code cannot be empty."));
        }

        List<HubSpotService.TranscriptSyncItem> items = new ArrayList<>();
        if (request.transcripts() != null && !request.transcripts().isEmpty()) {
            for (SyncRequest.TranscriptItem item : request.transcripts()) {
                if (item != null && item.text() != null && !item.text().trim().isEmpty()) {
                    items.add(new HubSpotService.TranscriptSyncItem(
                            item.text(),
                            item.actionPoints(),
                            item.tasks()
                    ));
                }
            }
        } else if (request.text() != null && !request.text().trim().isEmpty()) {
            items.add(new HubSpotService.TranscriptSyncItem(request.text(), null, null));
        }

        if (items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The transcript text cannot be empty."));
        }

        try {
            HubSpotService.MultiSyncResult result =
                    hubSpotService.publishApprovedTranscripts(request.dealCode(), items);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "dealId", result.dealId(),
                    "noteId", result.noteIds().isEmpty() ? "" : result.noteIds().get(0),
                    "noteIds", result.noteIds()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record SyncRequest(
            String dealCode,
            String text,
            List<TranscriptItem> transcripts
    ) {
        public record TranscriptItem(
                String text,
                List<Map<String, Object>> actionPoints,
                List<Map<String, Object>> tasks
        ) {}
    }
}
