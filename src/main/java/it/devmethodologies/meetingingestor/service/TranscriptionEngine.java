package it.devmethodologies.meetingingestor.service;

import java.nio.file.Path;

public interface TranscriptionEngine {
    /**
     * Transcribe the given recording
     * @param audioFile the recording on the disk
     * @param language a language code
     * @return  the transcript text
     * @throws RuntimeException if the file can not be transcribed
     */
    String transcribe(Path audioFile, String language);
}
