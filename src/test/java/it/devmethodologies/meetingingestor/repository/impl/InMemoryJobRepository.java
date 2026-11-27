package it.devmethodologies.meetingingestor.repository.impl;

import it.devmethodologies.meetingingestor.model.JobStatus;
import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import it.devmethodologies.meetingingestor.repository.JobRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryJobRepository implements JobRepository {
    private final Map<String, TranscriptionJob> jobs = new ConcurrentHashMap<>();
    @Override
    public  void save(TranscriptionJob job){
        Objects.requireNonNull(job, "job must not be null");
        jobs.put(job.id(), job);
    }
    @Override
    public Optional<TranscriptionJob> findById(String id){
        if (id == null){
            return  Optional.empty();
        }
        return  Optional.ofNullable(jobs.get(id));
    }
    @Override
    public List<TranscriptionJob> findByStatus(JobStatus status) {
        return jobs.values().stream()
                .filter(job -> job.status() == status)
                .toList();
    }
}
