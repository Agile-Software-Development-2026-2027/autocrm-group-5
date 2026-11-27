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
            BufferingClientHttpRequestFactory factory = new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory());
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

    public ExtractionResponse extract(ExtractionRequest request) {
        log.info("Sending request :" + request);
        return restClient.post()
                .uri("/api/v1/transcripts/extractions/")
                .header("Keypoints-API-Key", apiKey)          // the internal key
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ExtractionResponse.class);
    }
}
