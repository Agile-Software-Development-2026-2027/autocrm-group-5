package it.devmethodologies.meetingingestor.controller;

import it.devmethodologies.meetingingestor.service.impl.HubSpotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HubSpotControllerTest {

    @Mock
    private HubSpotService hubSpotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HubSpotController(hubSpotService))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void missingDealCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Approved transcript\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The deal code cannot be empty."));

        verify(hubSpotService, never()).publishApprovedTranscript(anyString(), anyString());
    }

    @Test
    void emptyDealCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"   \",\"text\":\"Approved transcript\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The deal code cannot be empty."));

        verify(hubSpotService, never()).publishApprovedTranscript(anyString(), anyString());
    }

    @Test
    void missingTextReturns400() throws Exception {
        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"ACME-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The transcript text cannot be empty."));

        verify(hubSpotService, never()).publishApprovedTranscript(anyString(), anyString());
    }

    @Test
    void emptyTextReturns400() throws Exception {
        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"ACME-1\",\"text\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The transcript text cannot be empty."));

        verify(hubSpotService, never()).publishApprovedTranscript(anyString(), anyString());
    }

    @Test
    void validInputCallsServiceAndReturnsNoteId() throws Exception {
        when(hubSpotService.publishApprovedTranscript("ACME-1", "Approved transcript"))
                .thenReturn(new HubSpotService.ApprovedNote("deal-42", "note-7"));

        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"ACME-1\",\"text\":\"Approved transcript\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").value("note-7"))
                .andExpect(jsonPath("$.dealId").value("deal-42"))
                .andExpect(jsonPath("$.status").value("success"));

        verify(hubSpotService).publishApprovedTranscript("ACME-1", "Approved transcript");
    }

    @Test
    void dealLookupErrorIsReturnedAndNoteIsNotCreated() throws Exception {
        when(hubSpotService.publishApprovedTranscript("AMBIGUOUS", "Approved transcript"))
                .thenThrow(new IllegalArgumentException(
                        "Multiple deals found with code: AMBIGUOUS. No note was created."));

        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"AMBIGUOUS\",\"text\":\"Approved transcript\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Multiple deals found with code: AMBIGUOUS. No note was created."));
    }
}
