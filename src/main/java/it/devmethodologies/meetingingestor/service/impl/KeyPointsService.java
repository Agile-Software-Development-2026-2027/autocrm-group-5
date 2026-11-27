package it.devmethodologies.meetingingestor.service.impl;

import it.devmethodologies.meetingingestor.model.KeyPointsTranscript;
import it.devmethodologies.meetingingestor.model.KeyPointsTranscriptPage;
import it.devmethodologies.meetingingestor.model.keypoint.ExtractionRequest;
import it.devmethodologies.meetingingestor.model.keypoint.ExtractionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Client of the keypoints-services microservice (Django REST).
 * Walking skeleton: it reads one transcript or a page of transcripts with their key points.
 */
@Service
@Slf4j
public class KeyPointsService {

    private final RestClient restClient;
    private final String apiKey;

    // Default = the local Django service started with run.ps1, so the backend starts without extra config.
    @Autowired
    public KeyPointsService(
            @Value("${keypoints.service.url:http://127.0.0.1:8701}") String baseUrl,
            @Value("${keypoints.service.apiKey:local-dev-key}") String apiKey) {
            // so buffer the body and send Content-Length instead of Transfer-Encoding: chunked.
            SimpleClientHttpRequestFactory simple = new SimpleClientHttpRequestFactory();
            // keypoints-services retries the LLM internally (up to ~60 s per attempt): wait up to 180 s (docs/api-contract.md).
            simple.setConnectTimeout(Duration.ofSeconds(5));
            simple.setReadTimeout(Duration.ofSeconds(180));
            BufferingClientHttpRequestFactory factory = new BufferingClientHttpRequestFactory(simple);
            this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
            this.apiKey = apiKey;
    }

    // Separate constructor to inject a dedicated client in tests.
    KeyPointsService(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    /**
     * @return the transcript, or empty if keypoints-services answers 404
     * @throws KeyPointsUnavailableException if the service is down or answers with another error
     */
    public Optional<KeyPointsTranscript> getTranscript(long id) {
        try {
            KeyPointsTranscript transcript = restClient.get()
                    .uri("/api/v1/transcripts/{id}/", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(KeyPointsTranscript.class);
            return Optional.ofNullable(transcript);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            // Technical failure: keep the cause for the logs, never show it to the caller.
            throw new KeyPointsUnavailableException("keypoints-services call failed for transcript " + id, e);
        }
    }

    /**
     * @param page page number, starting from 1 (20 transcripts per page, newest first)
     * @return the page, or empty if keypoints-services answers 404 (page out of range)
     * @throws KeyPointsUnavailableException if the service is down or answers with another error
     */
    public Optional<KeyPointsTranscriptPage> listTranscripts(int page) {
        try {
            KeyPointsTranscriptPage result = restClient.get()
                    .uri("/api/v1/transcripts/?page={page}", page)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(KeyPointsTranscriptPage.class);
            return Optional.ofNullable(result);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new KeyPointsUnavailableException("keypoints-services call failed for page " + page, e);
        }
    }

    /** Technical exception: keypoints-services is unreachable or failed. */
    public static class KeyPointsUnavailableException extends RuntimeException {
        public KeyPointsUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** keypoints-services answered 400: the message names the invalid field and is safe to show. */
    public static class ExtractionRejectedException extends RuntimeException {
        public ExtractionRejectedException(String message) {
            super(message);
        }
    }

    /** keypoints-services answered 429: the LLM quota is exhausted. */
    public static class ExtractionRateLimitedException extends RuntimeException {
        private final String retryAfter;

        public ExtractionRateLimitedException(String retryAfter) {
            super("keypoints-services rate limited");
            this.retryAfter = retryAfter;
        }

        /** Seconds, as sent in the Retry-After header; null when unknown. */
        public String getRetryAfter() {
            return retryAfter;
        }
    }

    /**
     * @throws ExtractionRejectedException    if keypoints-services answers 400
     * @throws ExtractionRateLimitedException if it answers 429
     * @throws KeyPointsUnavailableException  for 401 (wrong key = our configuration error), 5xx, timeout, service down
     */
    public ExtractionResponse extract(ExtractionRequest request) {
        // Never log the transcript (personal data): only its size.
        log.info("Sending extraction request ({} chars)", request.transcript().length());
        try {
            ExtractionResponse response = restClient.post()
                    .uri("/api/v1/extractions/")
                    .header("Keypoints-API-Key", apiKey)          // the internal key
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ExtractionResponse.class);
            if (response == null || response.actionPoints() == null || response.tasks() == null) {
                throw new KeyPointsUnavailableException("keypoints-services sent a body that does not match the schema", null);
            }
            return response;
        } catch (HttpClientErrorException e) {
            switch (e.getStatusCode().value()) {
                case 400 -> throw new ExtractionRejectedException(errorMessageOf(e));
                case 429 -> throw new ExtractionRateLimitedException(e.getResponseHeaders() == null
                        ? null : e.getResponseHeaders().getFirst("Retry-After"));
                case 401 -> {
                    log.error("keypoints-services rejected our API key: check keypoints.service.apiKey");
                    throw new KeyPointsUnavailableException("keypoints-services answered 401", e);
                }
                default -> throw new KeyPointsUnavailableException("keypoints-services answered " + e.getStatusCode(), e);
            }
        } catch (RestClientException e) {
            throw new KeyPointsUnavailableException("keypoints-services extraction call failed", e);
        }
    }

    private static String errorMessageOf(RestClientResponseException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null && body.get("error") instanceof String message && !message.isBlank()) {
                return message;
            }
        } catch (RuntimeException ignored) {
            // fall through to the generic message
        }
        return "The request is not valid.";
    }
}
