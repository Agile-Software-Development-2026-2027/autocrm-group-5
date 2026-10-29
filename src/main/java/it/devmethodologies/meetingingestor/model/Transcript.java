package it.devmethodologies.meetingingestor.model;

import java.util.List;


public record Transcript(String id, String title, Long date, List<Sentence> sentences) {}
