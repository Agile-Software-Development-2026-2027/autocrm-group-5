package it.devmethodologies.meetingingestor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One page of transcripts from keypoints-services:
 * GET /api/v1/transcripts/?page=1 -> {"count": 3, "next": ..., "previous": ..., "results": [...]}
 * "next"/"previous" are ignored on purpose: they contain the internal URL of the microservice.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeyPointsTranscriptPage(long count, List<KeyPointsTranscript> results) {
}
