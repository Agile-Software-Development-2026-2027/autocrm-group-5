package it.devmethodologies.meetingingestor.controller;

import it.devmethodologies.meetingingestor.model.JobStatus;
import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import it.devmethodologies.meetingingestor.service.impl.TranscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/transcriptions")
@CrossOrigin(origins = "*")
public class TranscriptionController {
    private final TranscriptionService transcriptionService;
    public TranscriptionController(TranscriptionService transcriptionService){
        this.transcriptionService = transcriptionService;
    }
    //SCRUM-56 : Status of a Job
    @GetMapping("/{jobId}")
    public ResponseEntity<?> status(@PathVariable String jobId){
        Optional<TranscriptionJob> job = transcriptionService.findJob(jobId);
        if (job.isEmpty()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","Job ID not found."));
        }
        return ResponseEntity.ok(StatusResponse.from(job.get()));
    }
    public record StatusResponse(String jobId, JobStatus status, String transcript){
        static StatusResponse from(TranscriptionJob job){
            return new StatusResponse(job.id(), job.status(),job.transcript());
        }
    }
}
