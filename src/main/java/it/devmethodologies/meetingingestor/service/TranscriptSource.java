package it.devmethodologies.meetingingestor.service;

import it.devmethodologies.meetingingestor.model.Transcript;

public interface TranscriptSource {

    Transcript getTranscript(String meetingId);
}
