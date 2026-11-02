package it.devmethodologies.meetingingestor.service.impl;

import it.devmethodologies.meetingingestor.model.Transcript;
import it.devmethodologies.meetingingestor.service.TranscriptSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MeetingService {

    public record HubSpotFileResponse(String id) {}
    public record HubSpotDeal(String id) {}
    public record HubSpotSearchResponse(HubSpotDeal[] results) {}

    private final TranscriptSource transcriptSource;
    private final RestClient hubSpotClient;

    public MeetingService(
            TranscriptSource transcriptSource,
            @Value("${hubspot.api.token}") String hubSpotToken) {
        this.transcriptSource = transcriptSource;
        this.hubSpotClient = RestClient.builder()
                .baseUrl("https://api.hubapi.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + hubSpotToken)
                .build();
    }

    @Async
    public void processMeetingTranscript(String meetingId) {


        Transcript transcript = transcriptSource.getTranscript(meetingId);
        log.info("RECEIVED Transcript From Fireflies: {}", transcript);


        String formattedText = transcript.sentences().stream()
                .map(s -> s.speakerName() + ": " + s.text())
                .collect(Collectors.joining("\n"));


        String uploadedFileId = uploadTranscriptFile(transcript.id(), formattedText);
        log.info("File caricato su HubSpot con ID: {}", uploadedFileId);


        createHubSpotCall(transcript, formattedText, uploadedFileId);
    }

    private String uploadTranscriptFile(String transcriptId, String content) {
        ByteArrayResource fileResource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "transcript-" + transcriptId + ".txt";
            }
        };

        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("file", fileResource);
        multipartBody.add("folderPath", "/transcripts");
        multipartBody.add("options", "{\"access\": \"PRIVATE\"}");

        HubSpotFileResponse response = hubSpotClient.post()
                .uri("/files/2026-09/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody)
                .retrieve()
                .body(HubSpotFileResponse.class);

        return response != null ? response.id() : "";
    }

    private void createHubSpotCall(Transcript transcript, String formattedText, String fileId) {
        String timestamp = transcript.date() != null
                ? String.valueOf(transcript.date())
                : Instant.now().toString();

        // Proprietà base della chiamata
        Map<String, Object> properties = Map.of(
                "hs_timestamp", timestamp,
                "hs_call_title", transcript.title(),
                "hs_call_body", formattedText,
                "hs_call_status", "COMPLETED",
                "hs_attachment_ids", fileId
        );


        String dealId = searchDealByName(transcript.title());
        Map<String, Object> payload;


        if (dealId != null) {
            log.info("Trattativa '{}' trovata con ID {}. Associo la chiamata.", transcript.title(), dealId);
            payload = Map.of(
                    "properties", properties,
                    "associations", List.of(
                            Map.of(
                                    "to", Map.of("id", dealId),
                                    "types", List.of(Map.of(
                                            "associationCategory", "HUBSPOT_DEFINED",
                                            "associationTypeId", 206 // 206 identifica l'associazione Call -> Deal su HubSpot
                                    ))
                            )
                    )
            );
        } else {
            // Fallback: se non trova la trattativa, crea la chiamata "orfana"
            log.warn("Nessuna trattativa trovata con il nome '{}'. La chiamata non avrà associazioni.", transcript.title());
            payload = Map.of("properties", properties);
        }


        String callResponse = hubSpotClient.post()
                .uri("/crm/objects/2026-09/calls")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        log.info("Chiamata registrata su HubSpot: {}", callResponse);
    }

    // Metodo helper per cercare un Deal su HubSpot in base al nome
    private String searchDealByName(String dealName) {
        Map<String, Object> searchPayload = Map.of(
                "filterGroups", List.of(
                        Map.of("filters", List.of(
                                Map.of(
                                        "propertyName", "dealname",
                                        "operator", "EQ",
                                        "value", dealName
                                )
                        ))
                )
        );

        try {
            HubSpotSearchResponse response = hubSpotClient.post()
                    .uri("/crm/v3/objects/deals/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(searchPayload)
                    .retrieve()
                    .body(HubSpotSearchResponse.class);

            if (response != null && response.results() != null && response.results().length > 0) {
                return response.results()[0].id(); // Ritorna l'ID del primo Deal corrispondente
            }
        } catch (Exception e) {
            log.error("Errore durante la ricerca della trattativa '{}': {}", dealName, e.getMessage());
        }
        return null;
    }
}