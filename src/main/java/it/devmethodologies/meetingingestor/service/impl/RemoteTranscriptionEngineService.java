package it.devmethodologies.meetingingestor.service.impl;

import java.net.http.HttpClient;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import it.devmethodologies.meetingingestor.service.TranscriptionEngine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RemoteTranscriptionEngineService implements TranscriptionEngine {
    private final RestClient restClient;

    @Autowired 
    public RemoteTranscriptionEngineService(@Value("${transcription.service.url}") String baseUrl) {
        this(buildRestClient(baseUrl));
    }

    // used by tests to inject a mocked RestClient
    public RemoteTranscriptionEngineService(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                // uvicorn only supports HTTP_1_1, HTTP_2 is experimental
                // https://uvicorn.dev/concepts/http2/
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Override
    public String transcribe(Path audioFile, String language) {
        log.info("Sending transcription request: file={}, language={}",
                audioFile.getFileName(), language);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(audioFile));
        if (language != null) {
            body.add("language", language);
        }

        TranscriptionResponse response;
        try {
            response = restClient.post()
                    .uri("/api/v1/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(TranscriptionResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Transcription service returned {} for file={}",
                    e.getStatusCode(), audioFile.getFileName());
            log.debug("Error response body: {}", e.getResponseBodyAsString());
            throw e;
        } catch (RuntimeException e) {
            log.error("Transcription request failed for file={}",
                    audioFile.getFileName(), e);
            throw e;
        }

        if (response == null || response.text() == null) {
            log.error("Invalid response from transcription service for file={}",
                    audioFile.getFileName());
            throw new IllegalStateException("Invalid response from transcription service");
        }

        log.info("Transcription received: file={}, chars={}",
                audioFile.getFileName(), response.text().length());
        return response.text();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TranscriptionResponse(String text) {
    }
}
