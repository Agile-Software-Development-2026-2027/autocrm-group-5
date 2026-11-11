package it.devmethodologies.meetingingestor.service.impl;

import it.devmethodologies.meetingingestor.model.JobStatus;
import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import it.devmethodologies.meetingingestor.repository.impl.InMemoryJobRepository;
import it.devmethodologies.meetingingestor.service.TranscriptionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptionServiceTest {

    @TempDir
    Path tempDir;

    private final InMemoryJobRepository repository = new InMemoryJobRepository();

    @Test
    void successfulTranscriptionMarksJobDoneAndDeletesFile() throws Exception {
        Path audio = Files.createTempFile(tempDir, "audio", ".m4a");
        TranscriptionEngine engine = (file, language) -> "Hello from the meeting";
        System.out.println(audio);

        TranscriptionService service =
                new TranscriptionService(repository, engine, Runnable::run, Duration.ofSeconds(5));

        String id = service.submit(audio, null);

        TranscriptionJob job = service.findJob(id).orElseThrow();
        assertEquals(JobStatus.DONE, job.status());
        assertEquals("Hello from the meeting", job.transcript());
        assertFalse(Files.exists(audio));
    }

    @Test
    void engineFailureMarksJobFailedAndDeletesFile() throws Exception {
        Path audio = Files.createTempFile(tempDir, "audio", ".m4a");
        TranscriptionEngine engine = (file, language) -> {
            throw new IllegalArgumentException("Audio could not be decoded");
        };
        TranscriptionService service =
                new TranscriptionService(repository, engine, Runnable::run, Duration.ofSeconds(5));

        String id = service.submit(audio, null);

        TranscriptionJob job = service.findJob(id).orElseThrow();
        assertEquals(JobStatus.FAILED, job.status());
        assertEquals("Audio could not be decoded", job.errorMessage());
        assertFalse(Files.exists(audio));
    }
    @Test
    void submitReturnsImmediatelyWithProcessingJob() throws Exception {
        Path audio = Files.createTempFile(tempDir, "audio", ".m4a");
        TranscriptionEngine engine = (file, language) -> "never called";
        Executor neverRuns = task -> { };
        TranscriptionService service =
                new TranscriptionService(repository, engine, neverRuns, Duration.ofHours(1));

        String id = service.submit(audio, null);

        TranscriptionJob job = service.findJob(id).orElseThrow();
        assertEquals(JobStatus.PROCESSING, job.status());
        assertNull(job.transcript());
    }

    @Test
    void slowEngineTimesOut() throws Exception {
        Path audio = Files.createTempFile(tempDir, "audio", ".m4a");
        TranscriptionEngine slowEngine = (file, language) -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "too late";
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TranscriptionService service =
                    new TranscriptionService(repository, slowEngine, executor, Duration.ofMillis(100));

            String id = service.submit(audio, null);

            TranscriptionJob job = waitUntilFinished(service, id, audio);
            assertEquals(JobStatus.FAILED, job.status());
            assertEquals("Transcription timed out", job.errorMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    private static TranscriptionJob waitUntilFinished(TranscriptionService service, String id, Path audio)
            throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            TranscriptionJob job = service.findJob(id).orElseThrow();
            if (job.status() != JobStatus.PROCESSING && !Files.exists(audio)) {
                return job;
            }
            Thread.sleep(50);
        }
        return fail("Job " + id + " did not finish (and clean up) within 2 seconds");
    }
}