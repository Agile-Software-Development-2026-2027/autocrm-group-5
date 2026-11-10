package it.devmethodologies.meetingingestor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptionJobTest {
    @Test
    void jobWithoutIdIsRejected(){
        assertThrows(NullPointerException.class, ()->TranscriptionJob.processing(null));
    }
    @Test
    void finishedJobCannotChangeAgain(){
        TranscriptionJob failed = TranscriptionJob.processing("job-1").failed("boom");
        assertThrows(IllegalStateException.class, ()->failed.done("late result"));
        assertThrows(IllegalStateException.class, ()->failed.failed("second failure"));
    }
}