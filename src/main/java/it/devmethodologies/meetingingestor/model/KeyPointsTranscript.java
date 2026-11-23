package it.devmethodologies.meetingingestor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A transcript as returned by keypoints-services (Django):
 * GET /api/v1/transcripts/{id}/ -> {"id": 1, "text": "...", "key_points": [...], "created_at": "..."}
 * Fields that Django doesn't send stay null and are omitted from our JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeyPointsTranscript(
        Long id,
        String text,
        @JsonProperty("key_points") List<String> keyPoints,
        @JsonProperty("created_at") String createdAt) {
}
