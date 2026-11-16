package it.devmethodologies.meetingingestor.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class RemoteTranscriptionEngineServiceTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient.RequestBodySpec requestSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Captor
    private ArgumentCaptor<MultiValueMap<String, Object>> bodyCaptor;

    private RemoteTranscriptionEngineService remoteService;

    @TempDir
    Path tempDir;
    private Path audio;

    @BeforeEach
    void setUp() throws IOException {
        when(restClient.post().uri("/api/v1/transcriptions")).thenReturn(requestSpec);
        when(requestSpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(requestSpec);
        when(requestSpec.body(any(Object.class))).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);

        remoteService = new RemoteTranscriptionEngineService(restClient);

        audio = tempDir.resolve("audio.mp3");
        Files.write(audio, "audio mock".getBytes());
    }

    @Test
    void transcribe_returnsTextFromResponse() throws Exception {
        when(responseSpec.body(ArgumentMatchers.<Class<Object>>any()))
                .thenReturn(new RemoteTranscriptionEngineService.TranscriptionResponse("transcription mock"));

        assertThat(remoteService.transcribe(audio, "en")).isEqualTo("transcription mock");
    }

    @Test
    void transcribe_sendsFileAndLanguage() throws Exception {
        when(responseSpec.body(ArgumentMatchers.<Class<Object>>any()))
                .thenReturn(new RemoteTranscriptionEngineService.TranscriptionResponse("transcription mock"));

        remoteService.transcribe(audio, "en");

        verify(requestSpec).body(bodyCaptor.capture());
        MultiValueMap<String, Object> sent = bodyCaptor.getValue();
        assertThat(sent.getFirst("language")).isEqualTo("en");
        assertThat(sent.containsKey("file")).isTrue();
    }

    @Test
    void transcribe_omitsLanguage_whenNull() throws Exception {
        when(responseSpec.body(ArgumentMatchers.<Class<Object>>any()))
                .thenReturn(new RemoteTranscriptionEngineService.TranscriptionResponse("transcription mock"));

        remoteService.transcribe(audio, null);

        verify(requestSpec).body(bodyCaptor.capture());
        MultiValueMap<String, Object> sent = bodyCaptor.getValue();
        assertThat(sent.containsKey("language")).isFalse();
    }

    @Test
    void transcribe_throwsIllegalState_whenResponseIsNull() {
        when(responseSpec.body(ArgumentMatchers.<Class<Object>>any())).thenReturn(null);

        assertThatThrownBy(() -> remoteService.transcribe(audio, "en"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid response");
    }

    @Test
    void transcribe_propagatesServerError() {
        when(responseSpec.body(ArgumentMatchers.<Class<Object>>any()))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "unavailable",
                        null,
                        null,
                        null));

        assertThatThrownBy(() -> remoteService.transcribe(audio, "en"))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
