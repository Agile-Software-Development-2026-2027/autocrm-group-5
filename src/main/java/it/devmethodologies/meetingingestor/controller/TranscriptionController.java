package it.devmethodologies.meetingingestor.controller;

import it.devmethodologies.meetingingestor.model.JobStatus;
import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import it.devmethodologies.meetingingestor.service.impl.TranscriptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


@RestController
@RequestMapping("/api/transcriptions")
@CrossOrigin(origins = "*")
public class TranscriptionController {
    private final TranscriptionService transcriptionService;
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "m4a", "ogg", "oga", "opus", "webm", "flac", "aac", "mp4");
    private static final String INVALID_FILE_MESSAGE = "Invalid file format or file size exceeded.";
    private final long maxSizeBytes;

    public TranscriptionController(TranscriptionService transcriptionService, @Value("${autocrm.upload.max-size-mb:100}") long maxSizeMb) {
        this.transcriptionService = transcriptionService;
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
    }

    //SCRUM-76 : Status of a Job
    @GetMapping("/{jobId}")
    public ResponseEntity<?> status(@PathVariable String jobId) {
        Optional<TranscriptionJob> job = transcriptionService.findJob(jobId);
        if (job.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job ID not found."));
        }
        return ResponseEntity.ok(StatusResponse.from(job.get()));
    }

    public record StatusResponse(String jobId, JobStatus status, String transcript) {
        static StatusResponse from(TranscriptionJob job) {
            return new StatusResponse(job.id(), job.status(), job.transcript());
        }
    }

    // SCRUM-56: receive a recording and start its transcription
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (!isFileAccepted(file)) {
            return ResponseEntity.badRequest().body(Map.of("error", INVALID_FILE_MESSAGE));
        }
        Path tempFile;
        try {
            tempFile = saveToTempFile(file);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Could not store the uploaded file."));
        }

        String jobId = transcriptionService.submit(tempFile, null);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", JobStatus.PROCESSING));
    }

    private boolean isFileAccepted(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        if (file.getSize() > maxSizeBytes) {
            return false;
        }
        return AUDIO_EXTENSIONS.contains(extensionOfRecording(file.getOriginalFilename()));
    }

    private static Path saveToTempFile(MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile("recording-", "." + extensionOfRecording(file.getOriginalFilename()));
        try {
            file.transferTo(tempFile);
            return tempFile;
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    private static String extensionOfRecording(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // Files above spring.servlet.multipart.max-file-size: answer with the contract's 400 instead of Spring's default 413.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> fileTooLarge() {
        return ResponseEntity.badRequest().body(Map.of("error", INVALID_FILE_MESSAGE));
    }
}
