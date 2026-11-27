package it.devmethodologies.meetingingestor.repository.impl;

import it.devmethodologies.meetingingestor.entity.TranscriptEntity;
import it.devmethodologies.meetingingestor.model.JobStatus;
import it.devmethodologies.meetingingestor.model.Transcript;
import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import it.devmethodologies.meetingingestor.repository.JobRepository;
import it.devmethodologies.meetingingestor.repository.TranscriptRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class DatabaseJobRepository implements JobRepository {
    private final TranscriptRepository transcripts;
    public DatabaseJobRepository(TranscriptRepository transcripts){
        this.transcripts = transcripts;
    }
    @Override
    public void save(TranscriptionJob job){
        Objects.requireNonNull(job, "job must not be null");
        transcripts.save(TranscriptEntity.from(job));
    }
    @Override
    public Optional<TranscriptionJob>findById(String id){
        if (id == null){
            return Optional.empty();
        }
        return transcripts.findById(id).map(TranscriptEntity::toJob);
    }

    @Override
    public List<TranscriptionJob> findByStatus(JobStatus status){
        return transcripts.findByStatus(status).stream().map(TranscriptEntity::toJob).toList();
    }
}
