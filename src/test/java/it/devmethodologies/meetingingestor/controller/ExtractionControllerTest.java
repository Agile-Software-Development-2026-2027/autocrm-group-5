package it.devmethodologies.meetingingestor.controller;

import it.devmethodologies.meetingingestor.model.keypoint.ActionPoint;
import it.devmethodologies.meetingingestor.model.keypoint.ExtractionResponse;
import it.devmethodologies.meetingingestor.model.keypoint.Task;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.ExtractionRateLimitedException;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.ExtractionRejectedException;
import it.devmethodologies.meetingingestor.service.impl.KeyPointsService.KeyPointsUnavailableException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/extractions: the public endpoint of docs/api-contract.md (section 5). */
@ExtendWith(MockitoExtension.class)
class ExtractionControllerTest {

    private static final String URL = "/api/extractions";

    @Mock
    private KeyPointsService keyPointsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExtractionController(keyPointsService))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private org.springframework.test.web.servlet.ResultActions send(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void returns200WithActionPointsAndTasks() throws Exception {
        when(keyPointsService.extract(any())).thenReturn(new ExtractionResponse(
                List.of(new ActionPoint("Sconto 10% confermato", null)),
                List.of(new Task("Preparare contratto", null, "Laura", "2026-11-28", "HIGH")),
                "it", "gemini-3.5-flash-lite"));

        send("{\"transcript\":\"Marco: confermiamo lo sconto del 10%.\",\"language\":\"it\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionPoints[0].title").value("Sconto 10% confermato"))
                .andExpect(jsonPath("$.tasks[0].assignee").value("Laura"))
                .andExpect(jsonPath("$.language").value("it"))
                .andExpect(jsonPath("$.model").value("gemini-3.5-flash-lite"));
    }

    @Test
    void blankTranscriptReturns400WithoutCallingTheService() throws Exception {
        send("{\"transcript\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"))
                .andExpect(jsonPath("$.error").value("The transcript cannot be empty."));

        verify(keyPointsService, never()).extract(any());
    }

    @Test
    void missingTranscriptReturns400() throws Exception {
        send("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
        verify(keyPointsService, never()).extract(any());
    }

    @Test
    void tooLongTranscriptReturns400() throws Exception {
        send("{\"transcript\":\"" + "a".repeat(200_001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        verify(keyPointsService, never()).extract(any());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        send("{not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void rejectionFromKeypointsServicePassesTheMessageThrough() throws Exception {
        when(keyPointsService.extract(any())).thenThrow(new ExtractionRejectedException("language: Language must be \"it\" or \"en\"."));

        send("{\"transcript\":\"x\",\"language\":\"fr\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"))
                .andExpect(jsonPath("$.error").value("language: Language must be \"it\" or \"en\"."));
    }

    @Test
    void rateLimitReturns429AndForwardsRetryAfter() throws Exception {
        when(keyPointsService.extract(any())).thenThrow(new ExtractionRateLimitedException("30"));

        send("{\"transcript\":\"x\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    void rateLimitWithoutRetryAfterHasNoHeader() throws Exception {
        when(keyPointsService.extract(any())).thenThrow(new ExtractionRateLimitedException(null));

        send("{\"transcript\":\"x\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void unavailableServiceReturns502WithAGenericMessage() throws Exception {
        when(keyPointsService.extract(any()))
                .thenThrow(new KeyPointsUnavailableException("connection refused to 127.0.0.1:8701", new RuntimeException()));

        send("{\"transcript\":\"x\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("extraction_unavailable"))
                .andExpect(jsonPath("$.error").value("The extraction service is not available."));
    }
}
