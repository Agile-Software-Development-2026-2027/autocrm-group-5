package it.devmethodologies.meetingingestor.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class HubSpotServiceTest {

    private static final String SEARCH_URL = "https://api.hubapi.com/crm/v3/objects/deals/search";
    private static final String NOTES_URL = "https://api.hubapi.com/crm/v3/objects/notes";

    @Mock
    private RestTemplate restTemplate;

    private HubSpotService service;

    @BeforeEach
    void setUp() {
        service = new HubSpotService(restTemplate, "test-token");
    }

    @Test
    void oneDealReturnsItsId() {
        stubSearch(List.of(Map.of("id", "deal-42")));

        String dealId = service.findDealIdByCode("ACME-1");

        assertEquals("deal-42", dealId);

        HttpEntity<?> request = captureSingle(SEARCH_URL);
        assertEquals("Bearer test-token", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        List<String> searchedProperties = propertyNames(request.getBody());
        assertTrue(searchedProperties.contains("dealname"));
        assertTrue(searchedProperties.contains("hs_object_id"));
        assertTrue(request.getBody().toString().contains("ACME-1"));
        verify(restTemplate, never()).exchange(
                eq(NOTES_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void noDealReturnsClearErrorAndDoesNotCreateNote() {
        stubSearch(List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publishApprovedTranscript("MISSING", "Approved transcript"));

        assertTrue(error.getMessage().contains("No deal found"));
        assertTrue(error.getMessage().contains("MISSING"));
        verify(restTemplate, never()).exchange(
                eq(NOTES_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void severalDealsReturnClearErrorAndDoNotCreateNote() {
        stubSearch(List.of(Map.of("id", "deal-1"), Map.of("id", "deal-2")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publishApprovedTranscript("AMBIGUOUS", "Approved transcript"));

        assertTrue(error.getMessage().contains("Multiple deals found"));
        assertTrue(error.getMessage().contains("AMBIGUOUS"));
        verify(restTemplate, never()).exchange(
                eq(NOTES_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void notePayloadContainsTranscriptTimestampAndDealAssociation() {
        stubNoteCreation();

        String noteId = service.createAndAssociateNote("deal-42", "Approved transcript");

        assertEquals("note-1", noteId);
        Map<String, Object> body = asMap(captureSingle(NOTES_URL).getBody());
        Map<String, Object> properties = asMap(body.get("properties"));
        assertEquals("Approved transcript", properties.get("hs_note_body"));
        assertFalse(properties.get("hs_timestamp").toString().isBlank());
        assertEquals(List.of("hs_note_body", "hs_timestamp"), List.copyOf(properties.keySet()).stream().sorted().toList());

        Map<String, Object> association = asMap(asList(body.get("associations")).get(0));
        assertEquals("deal-42", asMap(association.get("to")).get("id"));
        Map<String, Object> type = asMap(asList(association.get("types")).get(0));
        assertEquals("HUBSPOT_DEFINED", type.get("associationCategory"));
        assertEquals(214, ((Number) type.get("associationTypeId")).intValue());
    }

    @Test
    void textAtMaxLengthCreatesOneNoteAndLongerTextIsSplit() {
        stubNoteCreation();
        String exact = "a".repeat(HubSpotService.MAX_NOTE_BODY_LENGTH);

        service.createAndAssociateNote("deal-42", exact);

        List<String> exactBodies = noteBodies();
        assertEquals(List.of(exact), exactBodies);

        String longer = exact + "trailing";
        service.createAndAssociateNote("deal-42", longer);

        List<String> allBodies = noteBodies();
        List<String> longerBodies = allBodies.subList(1, allBodies.size());
        assertTrue(longerBodies.size() > 1);
        assertEquals(longer, String.join("", longerBodies));
        assertTrue(longerBodies.stream().allMatch(chunk -> chunk.length() <= HubSpotService.MAX_NOTE_BODY_LENGTH));
    }

    @Test
    void hubSpotClientErrorBecomesClearError() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate)
                .exchange(eq(NOTES_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.createAndAssociateNote("deal-42", "Approved transcript"));

        assertTrue(error.getMessage().contains("HubSpot"));
        assertTrue(error.getMessage().contains("400"));
    }

    @Test
    void hubSpotServerErrorBecomesClearError() {
        doThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))
                .when(restTemplate)
                .exchange(eq(NOTES_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.createAndAssociateNote("deal-42", "Approved transcript"));

        assertTrue(error.getMessage().contains("HubSpot"));
        assertTrue(error.getMessage().contains("502"));
    }

    private void stubSearch(List<Map<String, Object>> results) {
        doReturn(ResponseEntity.ok(Map.of("results", results)))
                .when(restTemplate)
                .exchange(eq(SEARCH_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    private void stubNoteCreation() {
        AtomicInteger ids = new AtomicInteger();
        doAnswer(invocation -> ResponseEntity.ok(Map.of("id", "note-" + ids.incrementAndGet())))
                .when(restTemplate)
                .exchange(eq(NOTES_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    private HttpEntity<?> captureSingle(String url) {
        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(url), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));
        return captor.getValue();
    }

    private List<String> noteBodies() {
        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, org.mockito.Mockito.atLeastOnce()).exchange(
                eq(NOTES_URL), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));
        List<String> bodies = new ArrayList<>();
        for (HttpEntity<?> entity : captor.getAllValues()) {
            bodies.add(asMap(asMap(entity.getBody()).get("properties")).get("hs_note_body").toString());
        }
        return bodies;
    }

    private static List<String> propertyNames(Object body) {
        List<String> names = new ArrayList<>();
        for (Object group : asList(asMap(body).get("filterGroups"))) {
            for (Object filter : asList(asMap(group).get("filters"))) {
                names.add(asMap(filter).get("propertyName").toString());
            }
        }
        return names;
    }

    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }
}
