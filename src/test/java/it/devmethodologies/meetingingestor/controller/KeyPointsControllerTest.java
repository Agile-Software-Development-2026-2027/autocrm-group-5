package it.devmethodologies.meetingingestor.controller;

import it.devmethodologies.meetingingestor.model.KeyPointsTranscript;
import it.devmethodologies.meetingingestor.model.KeyPointsTranscriptPage;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.KeyPointsUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KeyPointsControllerTest {

    @Mock
    private KeyPointsService keyPointsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new KeyPointsController(keyPointsService))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void existingTranscriptReturns200WithKeyPoints() throws Exception {
        when(keyPointsService.getTranscript(2L)).thenReturn(Optional.of(new KeyPointsTranscript(
                2L, "Luca: two meetings were not synchronised.", List.of("Re-send the two transcripts"),
                "2026-11-22T09:00:00Z")));

        mockMvc.perform(get("/api/keypoints/transcripts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.key_points[0]").value("Re-send the two transcripts"));
    }

    @Test
    void unknownTranscriptReturns404() throws Exception {
        when(keyPointsService.getTranscript(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/keypoints/transcripts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Transcript not found."));
    }

    @Test
    void serviceDownReturns502WithoutTechnicalDetails() throws Exception {
        when(keyPointsService.getTranscript(1L))
                .thenThrow(new KeyPointsUnavailableException("connection refused", new RuntimeException()));

        mockMvc.perform(get("/api/keypoints/transcripts/1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("The key points service is not available."));
    }

    @Test
    void nonPositiveIdReturns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/keypoints/transcripts/0"))
                .andExpect(status().isBadRequest());

        verify(keyPointsService, never()).getTranscript(anyLong());
    }

    @Test
    void listReturns200WithCountPageAndResults() throws Exception {
        when(keyPointsService.listTranscripts(1)).thenReturn(Optional.of(new KeyPointsTranscriptPage(
                2, List.of(new KeyPointsTranscript(2L, "Luca: two meetings were not synchronised.", List.of("Re-send the two transcripts"), "2026-11-22T09:00:00Z"),
                           new KeyPointsTranscript(1L, "Anna: the integration must be live by January.", List.of(), "2026-11-22T09:00:00Z")))));

        mockMvc.perform(get("/api/keypoints/transcripts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.results[0].id").value(2))
                .andExpect(jsonPath("$.results[0].text").value("Luca: two meetings were not synchronised."));
    }

    @Test
    void listWithPageBelowOneReturns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/keypoints/transcripts").param("page", "0"))
                .andExpect(status().isBadRequest());

        verify(keyPointsService, never()).listTranscripts(anyInt());
    }
}
