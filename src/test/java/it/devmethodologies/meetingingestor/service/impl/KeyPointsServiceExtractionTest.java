package it.devmethodologies.meetingingestor.service.impl;

import it.devmethodologies.meetingingestor.model.keypoint.ExtractionRequest;
import it.devmethodologies.meetingingestor.model.keypoint.ExtractionResponse;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.ExtractionRateLimitedException;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.ExtractionRejectedException;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.KeyPointsUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies the backend -> keypoints-services call for extraction (the HTTP call is mocked):
 * it posts to the right URL, sends the internal API key, parses the response and maps each error as in
 * docs/api-contract.md (section 5).
 */
class KeyPointsServiceExtractionTest {

    private static final String URL = "/api/v1/extractions/";

    private MockRestServiceServer server;
    private KeyPointsService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new KeyPointsService(builder.build(), "test-key");
    }

    private static ExtractionRequest request() {
        return new ExtractionRequest("Marco: confermiamo lo sconto del 10%.", "it", null, null);
    }

    @Test
    void extractSendsTheInternalKeyAndParsesTheResponse() {
        String djangoBody = """
                {
                  "actionPoints": [{"title": "Sconto 10% confermato", "description": null}],
                  "tasks": [{"title": "Preparare contratto", "description": null,
                             "assignee": "Laura", "dueDate": "2026-11-28", "priority": "HIGH"}],
                  "language": "it",
                  "model": "gemini-3.5-flash-lite"
                }
                """;

        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Keypoints-API-Key", "test-key"))
                .andRespond(withSuccess(djangoBody, MediaType.APPLICATION_JSON));

        ExtractionResponse response = service.extract(request());

        assertThat(response.tasks()).hasSize(1);
        assertThat(response.tasks().get(0).assignee()).isEqualTo("Laura");
        assertThat(response.actionPoints().get(0).title()).isEqualTo("Sconto 10% confermato");
        assertThat(response.model()).isEqualTo("gemini-3.5-flash-lite");
        server.verify();
    }

    @Test
    void answer400BecomesARejectionWithTheServiceMessage() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"transcript: This field may not be blank.\",\"code\":\"invalid_request\"}"));

        assertThatThrownBy(() -> service.extract(request()))
                .isInstanceOf(ExtractionRejectedException.class)
                .hasMessage("transcript: This field may not be blank.");
    }

    @Test
    void answer429BecomesRateLimitedWithRetryAfter() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "30")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"quota\",\"code\":\"rate_limited\"}"));

        assertThatThrownBy(() -> service.extract(request()))
                .isInstanceOfSatisfying(ExtractionRateLimitedException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo("30"));
    }

    @Test
    void answer401IsOurConfigurationErrorAndBecomesUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"Invalid or missing API key.\",\"code\":\"unauthorized\"}"));

        assertThatThrownBy(() -> service.extract(request())).isInstanceOf(KeyPointsUnavailableException.class);
    }

    @Test
    void answers502And500BecomeUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> service.extract(request())).isInstanceOf(KeyPointsUnavailableException.class);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> service.extract(request())).isInstanceOf(KeyPointsUnavailableException.class);
    }

    @Test
    void bodyNotMatchingTheSchemaBecomesUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"unexpected\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.extract(request())).isInstanceOf(KeyPointsUnavailableException.class);
    }

    @Test
    void connectionFailureBecomesUnavailable() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new java.io.IOException("Connection refused");
        });

        assertThatThrownBy(() -> service.extract(request())).isInstanceOf(KeyPointsUnavailableException.class);
    }
}
