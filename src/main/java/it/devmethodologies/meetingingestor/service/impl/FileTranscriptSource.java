/*
package it.devmethodologies.meetingingestor.service.impl;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import it.devmethodologies.meetingingestor.model.Transcript;
import it.devmethodologies.meetingingestor.service.TranscriptSource;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class FileTranscriptSource implements TranscriptSource {

    private final JsonMapper jsonMapper;

    
    @Override
    public Transcript getTranscript(String meetingId) {

        ClassPathResource file = new ClassPathResource(meetingId + ".json");

        try (InputStream in = file.getInputStream()) {
            return jsonMapper.readValue(in, Transcript.class);   // JSON → oggetto Transcript
        } catch (IOException e) {
            throw new IllegalArgumentException("Trascrizione non trovata per il meeting " + meetingId, e);
        }
        
    }

}
*/