package it.devmethodologies.meetingingestor.repository;

import it.devmethodologies.meetingingestor.entity.TranscriptEntity;
import it.devmethodologies.meetingingestor.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptRepository extends JpaRepository<TranscriptEntity, String> {

    List<TranscriptEntity> findByStatus(JobStatus status);
}