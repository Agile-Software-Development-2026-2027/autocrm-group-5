package it.devmethodologies.meetingingestor.repository.impl;

import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryJobRepositoryTest {
    private InMemoryJobRepository repository = new InMemoryJobRepository();
    @Test
    void savedJobCanBeFoundById(){
        TranscriptionJob job = TranscriptionJob.processing("job-1");
        repository.save(job);
        assertEquals(Optional.of(job), repository.findById("job-1"));
    }
    @Test
    void savingJobWithSameIdReplacesIt(){
        TranscriptionJob processing = TranscriptionJob.processing("job-1");
        repository.save(processing);

        TranscriptionJob done = processing.done("Hello from the meeeting");
        repository.save(done);

        assertEquals(Optional.of(done), repository.findById("job-1"));

    }
    @Test
    void unUnknownIdReturnsEmpty(){
        assertTrue(repository.findById("does-not-exist").isEmpty());
    }
    @Test
    void nullIdRetunsEmpty(){
        assertTrue(repository.findById(null).isEmpty());
    }
}