package it.devmethodologies.meetingingestor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Sentence(@JsonProperty("speaker_name") String speakerName, String text) {}

