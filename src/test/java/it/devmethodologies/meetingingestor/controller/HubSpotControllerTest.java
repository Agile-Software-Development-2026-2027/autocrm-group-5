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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        verify(hubSpotService, never()).publishApprovedTranscripts(anyString(), any());
    }

    @Test
    void emptyDealCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"   \",\"text\":\"Approved transcript\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The deal code cannot be empty."));

        verify(hubSpotService, never()).publishApprovedTranscripts(anyString(), any());
    }

    @Test
    void missingTextReturns400() throws Exception {
        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"ACME-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The transcript text cannot be empty."));

        verify(hubSpotService, never()).publishApprovedTranscripts(anyString(), any());
    }

    @Test
    void emptyTextReturns400() throws Exception {
        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"ACME-1\",\"text\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The transcript text cannot be empty."));

        verify(hubSpotService, never()).publishApprovedTranscripts(anyString(), any());
    }

    @Test
    void emptyTranscriptsArrayReturns400() throws Exception {
        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"ACME-1\",\"transcripts\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The transcript text cannot be empty."));

        verify(hubSpotService, never()).publishApprovedTranscripts(anyString(), any());
    }

    @Test
    void validInputCallsServiceAndReturnsNoteId() throws Exception {
        when(hubSpotService.publishApprovedTranscripts(eq("ACME-1"), any()))
                .thenReturn(new HubSpotService.MultiSyncResult("deal-42", List.of("note-7")));

        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"ACME-1\",\"text\":\"Approved transcript\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").value("note-7"))
                .andExpect(jsonPath("$.noteIds[0]").value("note-7"))
                .andExpect(jsonPath("$.dealId").value("deal-42"))
                .andExpect(jsonPath("$.status").value("success"));

        verify(hubSpotService).publishApprovedTranscripts(eq("ACME-1"), any());
    }

    @Test
    void multipleTranscriptsPayloadReturnsAllNoteIds() throws Exception {
        when(hubSpotService.publishApprovedTranscripts(eq("ACME-1"), any()))
                .thenReturn(new HubSpotService.MultiSyncResult("deal-42", List.of("note-1", "note-2")));

        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"ACME-1\",\"transcripts\":[{\"text\":\"Part 1\"},{\"text\":\"Part 2\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").value("note-1"))
                .andExpect(jsonPath("$.noteIds[0]").value("note-1"))
                .andExpect(jsonPath("$.noteIds[1]").value("note-2"))
                .andExpect(jsonPath("$.dealId").value("deal-42"))
                .andExpect(jsonPath("$.status").value("success"));

        verify(hubSpotService).publishApprovedTranscripts(eq("ACME-1"), any());
    }

    @Test
    void dealLookupErrorIsReturnedAndNoteIsNotCreated() throws Exception {
        when(hubSpotService.publishApprovedTranscripts(eq("AMBIGUOUS"), any()))
                .thenThrow(new IllegalArgumentException(
                        "Multiple deals found with code: AMBIGUOUS. No note was created."));

        mockMvc.perform(post("/api/hubspot/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealCode\":\"AMBIGUOUS\",\"text\":\"Approved transcript\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Multiple deals found with code: AMBIGUOUS. No note was created."));
    }

    // SCRUM-101: GET /api/hubspot/deals returns list of deals
    @Test
    void getDealsReturns200WithDealsList() throws Exception {
        when(hubSpotService.getDeals()).thenReturn(List.of(
                new HubSpotService.HubSpotDeal("deal-1", "Alpha Deal", "qualifiedtobuy"),
                new HubSpotService.HubSpotDeal("deal-2", "Beta Deal", "contractsent")
        ));

        mockMvc.perform(get("/api/hubspot/deals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dealId").value("deal-1"))
                .andExpect(jsonPath("$[0].dealName").value("Alpha Deal"))
                .andExpect(jsonPath("$[0].dealStage").value("qualifiedtobuy"))
                .andExpect(jsonPath("$[1].dealId").value("deal-2"))
                .andExpect(jsonPath("$[1].dealName").value("Beta Deal"))
                .andExpect(jsonPath("$[1].dealStage").value("contractsent"));

        verify(hubSpotService).getDeals();
    }

    @Test
    void getDealsReturns500WhenServiceThrows() throws Exception {
        when(hubSpotService.getDeals()).thenThrow(new IllegalStateException("HubSpot down"));

        mockMvc.perform(get("/api/hubspot/deals"))
                .andExpect(status().isInternalServerError());

        verify(hubSpotService).getDeals();
    }
}
