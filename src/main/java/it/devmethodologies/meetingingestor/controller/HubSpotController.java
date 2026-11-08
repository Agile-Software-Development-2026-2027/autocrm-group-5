package it.devmethodologies.meetingingestor.controller;
// This is the Controller Layer. Its only job is to be the "door" that receives the HTTP request from the frontend, 
// checks if the data is valid, hands it to the Service layer, and returns the final HTTP response.

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
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
}

// SCRUM 67: Create the backend endpoint to receive approved payloads