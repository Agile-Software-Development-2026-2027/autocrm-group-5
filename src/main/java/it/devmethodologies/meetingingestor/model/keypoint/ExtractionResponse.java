package it.devmethodologies.meetingingestor.model.keypoint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractionResponse(List<ActionPoint> actionPoints,
                                 List<Task> tasks,
                                 String language,
                                 String model) {}