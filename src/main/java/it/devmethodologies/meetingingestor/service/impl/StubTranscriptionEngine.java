package it.devmethodologies.meetingingestor.service.impl;

import it.devmethodologies.meetingingestor.service.TranscriptionEngine;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;

@Component

public class StubTranscriptionEngine implements TranscriptionEngine {
    private  static final long PROCESSING_TIME_MS =2000;
    @Override
    public  String transcribe(Path audioFile, String language){
        if (!Files.exists(audioFile)){
            throw new IllegalArgumentException("recording not found: " +audioFile.getFileName() );
        }
        try {
            Thread.sleep(PROCESSING_TIME_MS);
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transcription was interrupted", e);
        }
        return "[Stub Script for " +audioFile.getFileName() + "]";
    }
}
