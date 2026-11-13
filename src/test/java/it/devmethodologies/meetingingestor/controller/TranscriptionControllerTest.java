package it.devmethodologies.meetingingestor.controller;

import it.devmethodologies.meetingingestor.model.TranscriptionJob;
import it.devmethodologies.meetingingestor.service.impl.TranscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TranscriptionControllerTest {
    @Mock
    private TranscriptionService transcriptionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TranscriptionController(transcriptionService, 1))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    // SCRUM-56: a valid audio file is saved to a temp file, passed to the service, and answered with 202 and the job id
    @Test
    void validAudioFileReturnsJobId() throws Exception {
        when(transcriptionService.submit(any(Path.class), isNull())).thenReturn("job-123");
        MockMultipartFile file = new MockMultipartFile("file", "recording-123.mp3", "audio/mpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/transcriptions/upload").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("processing"));
        ArgumentCaptor<Path> savedFile = ArgumentCaptor.forClass(Path.class);
        verify(transcriptionService).submit(savedFile.capture(), isNull());
        assertTrue(Files.exists(savedFile.getValue()));
        Files.deleteIfExists(savedFile.getValue());
    }

    // SCRUM-56: a non-audio file is rejected with 400 and no job is started
    @Test
    void nonAudioFileReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/transcriptions/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid file format or file size exceeded."));
        verify(transcriptionService, never()).submit(any(), any());
    }

    // SCRUM-56: a file over the size limit is rejected with 400, and no job is started
    @Test
    void fileOverSizeLimitReturns400() throws Exception {
        byte[] tooBig = new byte[1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "large-recording.mp3", "audio/mpeg", tooBig);
        mockMvc.perform(multipart("/api/transcriptions/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid file format or file size exceeded."));
        verify(transcriptionService, never()).submit(any(), any());
    }

    // SCRUM-56: a request without a file is rejected with 400, and no job is started
    @Test
    void missingFileReturns400() throws Exception {
        mockMvc.perform(multipart("/api/transcriptions/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid file format or file size exceeded."));
        verify(transcriptionService, never()).submit(any(), any());
    }

    // SCRUM-76: a job still processing returns 200 with status "processing" and a null transcript
    @Test
    void processingJobReturnsProcessingStatus() throws Exception {
        when(transcriptionService.findJob("job-123")).thenReturn(Optional.of(TranscriptionJob.processing("job-123")));
        mockMvc.perform(get("/api/transcriptions/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.transcript").isEmpty());
        verify(transcriptionService).findJob("job-123");

    }

    // SCRUM-76: a finished job returns 200 with status "done" and the transcript text
    @Test
    void doneJobReturnsTranscript() throws Exception {
        TranscriptionJob jobDone = TranscriptionJob.processing("job-123").done("Hello guys this is from Monday meeting!!");
        when(transcriptionService.findJob("job-123")).thenReturn(Optional.of(jobDone));
        mockMvc.perform(get("/api/transcriptions/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.transcript").value("Hello guys this is from Monday meeting!!"));
        verify(transcriptionService).findJob("job-123");

    }

    // SCRUM-76: a failed job returns 200 with status "failed" and a null transcript
    @Test
    void failedJobReturnsFailedStatus() throws Exception {
        TranscriptionJob failedJob = TranscriptionJob.processing("job-123").failed("Transcription timed out");
        when(transcriptionService.findJob("job-123")).thenReturn(Optional.of(failedJob));

        mockMvc.perform(get("/api/transcriptions/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.transcript").isEmpty());

        verify(transcriptionService).findJob("job-123");
    }

    // SCRUM-76: an unknown job id returns 404 with the contract message
    @Test
    void unknownJobIdReturns404() throws Exception {
        when(transcriptionService.findJob("unknown-id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/transcriptions/unknown-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Job ID not found."));

        verify(transcriptionService).findJob("unknown-id");
    }
}