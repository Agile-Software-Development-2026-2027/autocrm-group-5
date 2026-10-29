package it.devmethodologies.meetingingestor.service.impl;

import it.devmethodologies.meetingingestor.model.Transcript;
import it.devmethodologies.meetingingestor.service.TranscriptSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingService {

    private final TranscriptSource transcriptSource;

    @Async
    public void processMeetingTranscript(String meetingId) { // define obj that need to recive

        // call fireflies api to get the meeting transcript
        // call hubspot api to create the meeting transcript

        Transcript transcript = transcriptSource.getTranscript(meetingId);

        log.info(" RECIVED Transcript From Fireflies: {}", transcript);
    }
}
