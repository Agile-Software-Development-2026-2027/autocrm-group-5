package it.devmethodologies.meetingingestor.model.keypoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExtractionRequest(
        @NotBlank(message = "The transcript cannot be empty.")
        @Size(max = 200_000, message = "The transcript is too long (max 200000 characters).")
        String transcript,

        String language,
        String meetingTitle,
        String meetingDate
) {
}
