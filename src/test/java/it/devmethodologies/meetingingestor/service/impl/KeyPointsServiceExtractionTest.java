package it.devmethodologies.meetingingestor.service.impl;

import it.devmethodologies.meetingingestor.model.keypoint.ExtractionRequest;
import it.devmethodologies.meetingingestor.model.keypoint.ExtractionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies the backend -> Django call for extraction:
 * it posts to the right URL, sends the internal API key, and parses the response.
 */
class KeyPointsServiceExtractionTest {

    @Test
    void extractSendsTheInternalKeyAndParsesTheResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeyPointsService service = new KeyPointsService(builder.build(), "test-key");

        String djangoBody = """
                {
                  "actionPoints": [{"title": "Sconto 10% confermato", "description": null}],
                  "tasks": [{"title": "Preparare contratto", "description": null,
                             "assignee": "Laura", "dueDate": "2026-11-28", "priority": "HIGH"}],
                  "language": "it",
                  "model": "gemini-3.5-flash-lite"
                }
                """;

        server.expect(requestTo("/api/v1/transcripts/extractions/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Keypoints-API-Key", "test-key"))
                .andRespond(withSuccess(djangoBody, MediaType.APPLICATION_JSON));

        ExtractionResponse response = service.extract(
                new ExtractionRequest("Marco: confermiamo lo sconto del 10%.", "it", null, null));

        assertThat(response.tasks()).hasSize(1);
        assertThat(response.tasks().get(0).assignee()).isEqualTo("Laura");
        assertThat(response.actionPoints().get(0).title()).isEqualTo("Sconto 10% confermato");
        server.verify();
    }
}
