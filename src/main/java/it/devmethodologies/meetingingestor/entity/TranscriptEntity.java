package it.devmethodologies.meetingingestor.entity;

import it.devmethodologies.meetingingestor.model.JobStatus;
import it.devmethodologies.meetingingestor.model.TranscriptChannel;
import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "transcripts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class TranscriptEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private TranscriptChannel source;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "text", length = 1_000_000)
    private String transcript;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    public static TranscriptEntity from(TranscriptionJob job) {
        TranscriptEntity entity = new TranscriptEntity();
        entity.id = job.id();
        entity.title = job.title();
        entity.source = job.source();
        entity.status = job.status();
        entity.transcript = job.transcript();
        entity.errorMessage = job.errorMessage();
        entity.createdAt = job.createdAt();
        entity.updatedAt = job.updatedAt();
        return entity;
    }
    public TranscriptionJob toJob() {
        return new TranscriptionJob(id, title, source, status, transcript, errorMessage, createdAt, updatedAt);
    }
}
