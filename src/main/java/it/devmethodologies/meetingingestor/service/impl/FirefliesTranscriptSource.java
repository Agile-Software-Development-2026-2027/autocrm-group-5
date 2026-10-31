package it.devmethodologies.meetingingestor.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import it.devmethodologies.meetingingestor.model.Transcript;
import it.devmethodologies.meetingingestor.service.TranscriptSource;

@Component
// Preferisce il servizio Fireflies quando esistono più implementazioni di TranscriptSource.
@Primary
public class FirefliesTranscriptSource implements TranscriptSource {

    private static final String API_URL = "https://api.fireflies.ai/graphql";
    // Richiede i campi necessari per costruire il modello Transcript usato dal backend.
    private static final String TRANSCRIPT_QUERY = """
            query Transcript($transcriptId: String!) {
              transcript(id: $transcriptId) {
                id
                title
                date
                sentences {
                  speaker_name
                  text
                }
              }
            }
            """;

    private final RestClient restClient;
    private final String apiKey;

    // Crea il client HTTP predefinito e legge la chiave API dalla configurazione Spring.
    @Autowired
    public FirefliesTranscriptSource(@Value("${fireflies.api.key:}") String apiKey) {
        this(RestClient.create(), apiKey);
    }

    // Costruttore separato per poter fornire un client HTTP dedicato nei test.
    FirefliesTranscriptSource(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public Transcript getTranscript(String meetingId) {
        // Verifica la configurazione prima di inviare la richiesta a Fireflies.
        if (meetingId == null || meetingId.isBlank()) {
            throw new IllegalArgumentException("L'ID del meeting Fireflies è obbligatorio");
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("La variabile FIREFLIES_API_KEY non è configurata");
        }

        // Invia una richiesta GraphQL autenticata usando l'ID del meeting come variabile.
        FirefliesResponse response = restClient.post()
                .uri(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .body(new GraphQlRequest(TRANSCRIPT_QUERY, Map.of("transcriptId", meetingId)))
                .retrieve()
                .body(FirefliesResponse.class);

        // Distingue una risposta vuota dagli errori GraphQL e dall'assenza del meeting.
        if (response == null) {
            throw new IllegalStateException("Fireflies ha restituito una risposta vuota per il meeting " + meetingId);
        }
        if (response.errors() != null && !response.errors().isEmpty()) {
            String messages = response.errors().stream()
                    .map(GraphQlError::message)
                    .toList()
                    .toString();
            throw new IllegalStateException(
                    "Errore Fireflies per il meeting " + meetingId + ": " + messages);
        }
        if (response.data() == null || response.data().transcript() == null) {
            throw new IllegalStateException(
                    "Fireflies non ha trovato la trascrizione per il meeting " + meetingId);
        }

        return response.data().transcript();
    }

    // Tipi interni che rappresentano la richiesta e le parti rilevanti della risposta GraphQL.
    private record GraphQlRequest(String query, Map<String, String> variables) {}

    private record FirefliesResponse(Data data, List<GraphQlError> errors) {}

    private record Data(Transcript transcript) {}

    private record GraphQlError(String message) {}
}
