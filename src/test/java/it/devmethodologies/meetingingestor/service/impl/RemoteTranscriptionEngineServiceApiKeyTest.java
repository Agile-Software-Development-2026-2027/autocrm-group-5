package it.devmethodologies.meetingingestor.service.impl;
 
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
 
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
 
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.HttpClientErrorException;
 
import com.sun.net.httpserver.HttpServer;
 

class RemoteTranscriptionEngineServiceApiKeyTest {
    private static final String API_KEY_HEADER = "Transcription-API-Key";
    private static final String API_KEY = "secret key";
 
    @TempDir
    Path tempDir;
 
    private HttpServer server;
    private String baseUrl;
    private Path audio;
 
    private final AtomicReference<String> receivedApiKey = new AtomicReference<>();
 
    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/transcriptions", exchange -> {
            receivedApiKey.set(exchange.getRequestHeaders().getFirst(API_KEY_HEADER));
            exchange.getRequestBody().readAllBytes();
 
            if (!API_KEY.equals(receivedApiKey.get())) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
 
            byte[] response = "{\"text\":\"transcription mock\"}".getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
 
        audio = tempDir.resolve("audio.mp3");
        Files.write(audio, "audio mock".getBytes());
    }
 
    @AfterEach
    void tearDown() {
        server.stop(0);
    }
 
    @Test
    void transcribe_sendsApiKeyHeader() {
        RemoteTranscriptionEngineService service =
                new RemoteTranscriptionEngineService(baseUrl, API_KEY);
 
        String text = service.transcribe(audio, "en");
 
        assertThat(receivedApiKey.get()).isEqualTo(API_KEY);
        assertThat(text).isEqualTo("transcription mock");
    }
 
    @Test
    void transcribe_isRejected_whenApiKeyIsWrong() {
        RemoteTranscriptionEngineService service =
                new RemoteTranscriptionEngineService(baseUrl, "wrong key");
 
        assertThatThrownBy(() -> service.transcribe(audio, "en"))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}
 