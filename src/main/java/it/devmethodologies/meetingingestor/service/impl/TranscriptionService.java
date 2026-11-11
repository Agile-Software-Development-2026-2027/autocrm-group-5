package it.devmethodologies.meetingingestor.service.impl;

import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import it.devmethodologies.meetingingestor.repository.JobRepository;
import it.devmethodologies.meetingingestor.service.TranscriptionEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
public class TranscriptionService {
    private final JobRepository repository;
    private final TranscriptionEngine engine;
    private final Executor executor;
    private final Duration timeout;

    @Autowired
    public TranscriptionService(
            JobRepository repository,
            TranscriptionEngine engine,
            @Value("${autocrm.transcription.timeout-minutes:10}") long timeoutMinutes,
            @Value("${autocrm.transcription.max-concurrent-jobs:2}") int maxConcurrentJobs
    ) {
        this(repository, engine, Executors.newFixedThreadPool(maxConcurrentJobs),
                Duration.ofMinutes(timeoutMinutes));
    }

    TranscriptionService(JobRepository repository, TranscriptionEngine engine, Executor executor, Duration timeout) {
        this.repository = repository;
        this.engine = engine;
        this.executor = executor;
        this.timeout = timeout;
    }

    public Optional<TranscriptionJob> findJob(String id) {
        return repository.findById(id);
    }

    private void finish(String id, Path audioFile, String transcript, Throwable error) {
        try {
            TranscriptionJob job = repository.findById(id).orElseThrow();
            if (error == null) {
                repository.save(job.done((transcript)));
            } else {
                log.warn("Transcription {} Job failed", id, error);
                repository.save(job.failed(describe(error)));
            }
        } finally {
            deleteQuitely(audioFile);
        }
    }

    private static String describe(Throwable error) {
        Throwable cause = error;
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof TimeoutException) {
            return "Transcription timeout";
        }
        if (cause.getMessage() != null) {
            return cause.getMessage();
        }
        return "Transcription failed";
    }

    private static void deleteQuitely(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete temporary file {}", file, e);
        }
    }

    public String submit(Path audioFile, String lang) {
        Objects.requireNonNull(audioFile, "Audio file should not be null");
        String id = UUID.randomUUID().toString();
        repository.save(TranscriptionJob.processing(id));
        CompletableFuture
                .supplyAsync(() -> engine.transcribe(audioFile, lang), executor)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((transcript, error) -> finish(id, audioFile, transcript, error));
        return id;

    }
}
