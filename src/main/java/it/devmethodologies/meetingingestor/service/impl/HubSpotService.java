package it.devmethodologies.meetingingestor.service.impl;

// SCRUM 68 AND 69 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class HubSpotService {

    @Value("${hubspot.service.key:${hubspot.api.token:}}")
    private String hubspotToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String HUBSPOT_BASE_URL = "https://api.hubapi.com/crm/v3/objects";

    /**
     * SCRUM 68: Find the Deal by deal code
     */
    @SuppressWarnings("unchecked")
    public String findDealIdByCode(String dealCode) {
        String searchUrl = HUBSPOT_BASE_URL + "/deals/search";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(hubspotToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Search payload: find deals where dealname matches the dealCode
        Map<String, Object> searchBody = Map.of(
            "filterGroups", List.of(Map.of(
                "filters", List.of(Map.of(
                    "propertyName", "dealname",
                    "operator", "EQ",
                    "value", dealCode
                ))
            ))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(searchBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(searchUrl, request, Map.class);

        if (response.getBody() == null) {
            throw new IllegalArgumentException("No response received from HubSpot search API.");
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
        
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("No deal found with code: " + dealCode);
        }
        if (results.size() > 1) {
            throw new IllegalArgumentException("Multiple deals found with code: " + dealCode + ". Cannot determine which to update.");
        }

        return (String) results.get(0).get("id");
    }

    /**
     * SCRUM 69: Create a Note and associate it with the Deal
     */
    @SuppressWarnings("unchecked")
    public String createAndAssociateNote(String dealId, String transcriptText) {
        String noteUrl = HUBSPOT_BASE_URL + "/notes";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(hubspotToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // HubSpot limits note bodies to 65,536 characters.
        // For SCRUM 69, we truncate/split if it's too long.
        String safeText = transcriptText.length() > 65000 
                ? transcriptText.substring(0, 65000) + "... [Truncated for HubSpot limits]" 
                : transcriptText;

        Map<String, Object> noteBody = Map.of(
            "properties", Map.of(
                "hs_note_body", safeText
            ),
            "associations", List.of(Map.of(
                "to", Map.of("id", dealId),
                "types", List.of(Map.of(
                    "associationCategory", "HUBSPOT_DEFINED",
                    "associationTypeId", 214 // SCRUM 26: Deal to Note association
                ))
            ))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(noteBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(noteUrl, request, Map.class);

        if (response.getBody() == null || response.getBody().get("id") == null) {
            throw new IllegalStateException("Failed to create note on HubSpot.");
        }

        return (String) response.getBody().get("id");
    }
}
