package it.devmethodologies.meetingingestor.service.impl;

// SCRUM 68 AND 69
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HubSpotService {

    /**
     * HubSpot rejects {@code hs_note_body} values longer than 65,536 characters.
     */
    static final int MAX_NOTE_BODY_LENGTH = 65_536;

    private static final String HUBSPOT_BASE_URL = "https://api.hubapi.com/crm/v3/objects";
    private static final int NOTE_TO_DEAL_ASSOCIATION_TYPE_ID = 214;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final String hubspotToken;

    @Autowired
    public HubSpotService(@Value("${hubspot.service.key:${hubspot.api.token:}}") String hubspotToken) {
        this(new RestTemplate(), hubspotToken);
    }

    HubSpotService(RestTemplate restTemplate, String hubspotToken) {
        this.restTemplate = restTemplate;
        this.hubspotToken = hubspotToken;
    }

    public record ApprovedNote(String dealId, String noteId) {}

    /**
     * Looks up the deal and creates the associated note.
     * A failed lookup throws before any note is created.
     */
    public ApprovedNote publishApprovedTranscript(String dealCode, String transcriptText) {
        String dealId = findDealIdByCode(dealCode);
        String noteId = createAndAssociateNote(dealId, transcriptText);
        return new ApprovedNote(dealId, noteId);
    }

    /**
     * SCRUM 68: Find the Deal by deal code (deal name or deal id) through the CRM search API.
     */
    @SuppressWarnings("unchecked")
    public String findDealIdByCode(String dealCode) {
        Map<String, Object> searchBody = Map.of(
                "filterGroups", List.of(
                        filterGroup("dealname", dealCode),
                        filterGroup("hs_object_id", dealCode)
                )
        );

        ResponseEntity<Map<String, Object>> response = post(
                HUBSPOT_BASE_URL + "/deals/search",
                searchBody,
                "searching for the deal"
        );

        if (response.getBody() == null) {
            throw new IllegalArgumentException("No response received from HubSpot search API.");
        }

        List<Map<String, Object>> results =
                (List<Map<String, Object>>) response.getBody().get("results");

        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("No deal found with code: " + dealCode);
        }
        if (results.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple deals found with code: " + dealCode + ". No note was created.");
        }

        Object id = results.get(0).get("id");
        if (id == null || id.toString().isBlank()) {
            throw new IllegalArgumentException("HubSpot returned a deal without an id for code: " + dealCode);
        }
        return id.toString();
    }

    /**
     * SCRUM 69: Create one or more notes with the approved transcript and associate each
     * with the deal (note to deal, association type id 214). Returns the first note id.
     * Deal properties are not modified.
     */
    public String createAndAssociateNote(String dealId, String transcriptText) {
        String firstNoteId = null;
        for (String chunk : split(transcriptText, MAX_NOTE_BODY_LENGTH)) {
            String noteId = postNote(dealId, chunk);
            if (firstNoteId == null) {
                firstNoteId = noteId;
            }
        }
        if (firstNoteId == null) {
            throw new IllegalArgumentException("The transcript text cannot be empty.");
        }
        return firstNoteId;
    }

    private String postNote(String dealId, String text) {
        Map<String, Object> noteBody = Map.of(
                "properties", Map.of(
                        "hs_note_body", text,
                        "hs_timestamp", String.valueOf(Instant.now().toEpochMilli())
                ),
                "associations", List.of(Map.of(
                        "to", Map.of("id", dealId),
                        "types", List.of(Map.of(
                                "associationCategory", "HUBSPOT_DEFINED",
                                "associationTypeId", NOTE_TO_DEAL_ASSOCIATION_TYPE_ID
                        ))
                ))
        );

        ResponseEntity<Map<String, Object>> response = post(
                HUBSPOT_BASE_URL + "/notes",
                noteBody,
                "creating the note"
        );

        if (response.getBody() == null || response.getBody().get("id") == null) {
            throw new IllegalStateException("Failed to create note on HubSpot.");
        }
        return response.getBody().get("id").toString();
    }

    private ResponseEntity<Map<String, Object>> post(String url, Map<String, Object> body, String action) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(hubspotToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
        } catch (HttpStatusCodeException ex) {
            throw new IllegalStateException(
                    "HubSpot returned an error (" + ex.getStatusCode().value() + ") while " + action + ".",
                    ex
            );
        }
    }

    private static Map<String, Object> filterGroup(String propertyName, String value) {
        return Map.of(
                "filters", List.of(Map.of(
                        "propertyName", propertyName,
                        "operator", "EQ",
                        "value", value
                ))
        );
    }

    private static List<String> split(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += maxLength) {
            chunks.add(text.substring(start, Math.min(text.length(), start + maxLength)));
        }
        return chunks;
    }
}
