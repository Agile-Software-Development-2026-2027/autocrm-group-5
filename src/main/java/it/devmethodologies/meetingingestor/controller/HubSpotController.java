package it.devmethodologies.meetingingestor.controller;

// SCRUM 67: Endpoint for HubSpot approval

import it.devmethodologies.meetingingestor.service.impl.HubSpotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/hubspot")
@CrossOrigin(origins = "*")
public class HubSpotController {
    
    private final HubSpotService hubSpotService;

    // Spring Boot automatically injects the Service written for 68 & 69
    public HubSpotController(HubSpotService hubSpotService) {
        this.hubSpotService = hubSpotService;
    }

    // SCRUM 67: Create the backend endpoint to receive approved payloads
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncTranscript(@RequestBody SyncRequest request) {

        // Fulfill SCRUM 67: Validate payload and reject missing/empty fields
        if (request.dealCode() == null || request.dealCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The deal code cannot be empty."));
        }
        if (request.text() == null || request.text().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The transcript text cannot be empty."));
        }

        try {
            // Fulfill SCRUM 68: Find the deal
            String dealId = hubSpotService.findDealIdByCode(request.dealCode());
            
            // Fulfill SCRUM 69: Create the note
            String noteId = hubSpotService.createAndAssociateNote(dealId, request.text());
            
            // Return the result to the frontend
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "dealId", dealId,
                "noteId", noteId
            ));
            
        } catch (RuntimeException e) {
            // Returns clear errors if the deal isn't found or if multiple are found
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Simple DTO mapping the JSON from the frontend
    public record SyncRequest(String dealCode, String text) {}
}
