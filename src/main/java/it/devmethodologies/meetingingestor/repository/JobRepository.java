package it.devmethodologies.meetingingestor.repository;

import it.devmethodologies.meetingingestor.model.TranscriptionJob;

import java.util.Optional;

public interface JobRepository {
    /**
     * Stores the job. If a job with the same id already exists, it is replaced.
     */
    void save(TranscriptionJob job);

    /**
     * Returns the job with the given id, or an empty Optional if there is none.
     */
    Optional<TranscriptionJob> findById(String id);
}
