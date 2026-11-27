package it.devmethodologies.meetingingestor.model;

import javax.crypto.spec.PSource;
import java.time.Instant;
import java.util.Objects;

public record TranscriptionJob(
        String id,
        String title,
        TranscriptChannel source,
        JobStatus status,
        String transcript,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
    /** Title used when the caller gives none (keeps the Sprint 2 processing(id) working). */
    public static final String UNTITLED = "Untitled recording";
    public TranscriptionJob{
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
    public static TranscriptionJob processing(String id){
        return processing(id, UNTITLED);
    }
    public static TranscriptionJob processing(String id, String title){
        Instant now = Instant.now();
        return new TranscriptionJob(id, title, TranscriptChannel.MANUAL, JobStatus.PROCESSING, null, null,now, now);
    }
    public static TranscriptionJob fromFireflies(String meetingId, String title, String transcript){
        Instant now = Instant.now();
        return new TranscriptionJob(meetingId, title, TranscriptChannel.FIREFLIES, JobStatus.DONE, transcript, null, now, now);
    }
    public TranscriptionJob done(String transcript){
        requireProcessing();
        return new TranscriptionJob(id, title, source,JobStatus.DONE, transcript, null, createdAt, Instant.now());
    }
    public TranscriptionJob failed(String errorMessage){
        requireProcessing();
        return  new TranscriptionJob(id, title, source, JobStatus.FAILED, null,errorMessage, createdAt, Instant.now());
    }
    private void requireProcessing(){
        if (status!=JobStatus.PROCESSING){
            throw new IllegalStateException("Job" + id + "is already" + status.toJson());
        }
    }

}
