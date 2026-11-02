
package it.devmethodologies.meetingingestor.controller;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.devmethodologies.meetingingestor.service.impl.MeetingService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/webhooks")
public class FirefliesWebhookController {

    private static final String SIGNATURE_PREFIX = "sha256=";

    private final MeetingService meetingService;
    private final ObjectMapper objectMapper;
    private final String signingSecret;

    public FirefliesWebhookController(
            MeetingService meetingService,
            ObjectMapper objectMapper,
            @Value("${fireflies.webhook.signing.secret}") String signingSecret) {
        if (signingSecret.isBlank()) {
            throw new IllegalStateException("FIREFLIES_WEBHOOK_SIGNING_SECRET must not be blank");
        }
        this.meetingService = meetingService;
        this.objectMapper = objectMapper;
        this.signingSecret = signingSecret;
    }

    @GetMapping("/fireflies-test")
    public ResponseEntity<String> test(@RequestParam String meetingId) {
        meetingService.processMeetingTranscript(meetingId);

        return ResponseEntity.accepted()
                .body("Elaborazione avviata per il meeting " + meetingId);
    }

    @PostMapping("/fireflies")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature", required = false) String signature) {
        if (rawBody.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        if (!isSignatureValid(rawBody, signature)) {
            return ResponseEntity.status(401).build();
        }

        final JsonNode payload;
        try {
            payload = parsePayload(rawBody);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().build();
        }

        String meetingId = extractMeetingId(payload);
        if (meetingId == null) {
            return ResponseEntity.badRequest().build();
        }

        processMeetingTranscript(meetingId);
        return ResponseEntity.ok().build();
    }


    private JsonNode parsePayload(byte[] rawBody) throws JacksonException {
        return objectMapper.readTree(rawBody);
    }


    private String extractMeetingId(JsonNode payload) {
        JsonNode meetingId = payload == null ? null : payload.get("meeting_id");
        if (meetingId == null || !meetingId.isTextual() || meetingId.asText().isBlank()) {
            return null;
        }
        return meetingId.asText();
    }

    private void processMeetingTranscript(String meetingId) {
        meetingService.processMeetingTranscript(meetingId);
    }




/*  Cosa fa il metodo per verificare la firma della richiesta webhook di Fireflies
 Fireflies conosce un segreto condiviso, chiamato signingSecret.
Anche il nostro backend conosce lo stesso segreto.
Quando Fireflies invia una notifica, genera una firma crittografica del contenuto della richiesta.
Il nostro backend calcola autonomamente la stessa firma.
Se le due firme coincidono, possiamo considerare autentica la richiesta, a condizione che il secret sia custodito correttamente.
*/
    private boolean isSignatureValid(byte[] rawBody, String signature) {
        // Senza firma non possiamo verificare che la richiesta provenga da Fireflies.
        if (signature == null) {
            return false;
        }

        // Fireflies puo' inviare la firma preceduta da "sha256=".
        // Rimuoviamo il prefisso se presente, lasciando la parte esadecimale.
        String signatureHex = signature.regionMatches(true, 0, SIGNATURE_PREFIX, 0,
                SIGNATURE_PREFIX.length())
                ? signature.substring(SIGNATURE_PREFIX.length())
                : signature;

        final byte[] providedSignature;
        try {
            // Converte la firma ricevuta da testo esadecimale a byte.
            providedSignature = HexFormat.of().parseHex(signatureHex);
        } catch (IllegalArgumentException e) {
            // Una firma con caratteri o formato non validi non puo' essere autentica.
            return false;
        }

        try {
            // Calcola HMAC-SHA256 sul corpo originale usando il secret condiviso.
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedSignature = hmac.doFinal(rawBody);

            // Confronto a tempo costante per ridurre il rischio di attacchi temporali.
            return MessageDigest.isEqual(expectedSignature, providedSignature);
        } catch (GeneralSecurityException e) {
            // Un errore crittografico indica un problema di configurazione/runtime,
            // non una firma semplicemente non valida: viene quindi propagato.
            throw new IllegalStateException("Unable to verify Fireflies webhook signature", e);
        }
    }
}
