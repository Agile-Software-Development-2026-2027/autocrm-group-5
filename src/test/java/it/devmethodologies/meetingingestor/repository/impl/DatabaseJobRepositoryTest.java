package it.devmethodologies.meetingingestor.repository.impl;

import it.devmethodologies.meetingingestor.model.JobStatus;
import it.devmethodologies.meetingingestor.model.TranscriptChannel;
import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(DatabaseJobRepository.class)
class DatabaseJobRepositoryTest {
    @Autowired
    private DatabaseJobRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    // SCRUM-88: a saved job is read back from the database with all its fields; an unknown id finds nothing
    @Test
    void savedJobIsReadBackWithAllFields(){
        Instant created = Instant.parse("2026-11-27T10:15:30.123456Z");
        TranscriptionJob job = new TranscriptionJob("job-1", "meeting.mp3", TranscriptChannel.MANUAL,
                JobStatus.FAILED, null, "Transcription timed out", created, created);

        repository.save(job);
        entityManager.flush();
        entityManager.clear();

        assertEquals(Optional.of(job), repository.findById("job-1"));
        assertTrue(repository.findById("unknown").isEmpty());
    }

    // SCRUM-88: saving a job again with the same id updates its row instead of adding a second one
    @Test
    void savingSameIdUpdatesTheRow() {
        TranscriptionJob processing = TranscriptionJob.processing("job-2", "meeting.mp3");
        repository.save(processing);
        entityManager.flush();
        entityManager.clear();

        repository.save(processing.done("Hello from the meeting"));
        entityManager.flush();
        entityManager.clear();

        TranscriptionJob stored = repository.findById("job-2").orElseThrow();
        assertEquals(JobStatus.DONE, stored.status());
        assertEquals("Hello from the meeting", stored.transcript());
        assertEquals(List.of(stored), repository.findByStatus(JobStatus.DONE));
        assertTrue(repository.findByStatus(JobStatus.PROCESSING).isEmpty());
    }

}