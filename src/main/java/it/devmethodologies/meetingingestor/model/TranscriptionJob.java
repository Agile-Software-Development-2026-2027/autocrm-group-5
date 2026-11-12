package it.devmethodologies.meetingingestor.model;

import java.time.Instant;
import java.util.Objects;

public record TranscriptionJob(
        String id,
        JobStatus status,
        String transcript,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
    public TranscriptionJob{
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
    public static TranscriptionJob processing(String id){
        Instant now = Instant.now();
        return new TranscriptionJob(id, JobStatus.PROCESSING, null, null,now, now);
    }
    public TranscriptionJob done(String transcript){
        requireProcessing();
        return new TranscriptionJob(id, JobStatus.DONE, transcript, null, createdAt, Instant.now());
    }
    public TranscriptionJob failed(String errorMessage){
        requireProcessing();
        return  new TranscriptionJob(id, JobStatus.FAILED, null,errorMessage, createdAt, Instant.now());
    }
    private void requireProcessing(){
        if (status!=JobStatus.PROCESSING){
            throw new IllegalStateException("Job" + id + "is already" + status.toJson());
        }
    }

}
