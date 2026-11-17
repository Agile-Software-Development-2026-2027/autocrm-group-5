import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TimeoutError } from 'rxjs';
import { vi } from 'vitest';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import { TranscriptApprovalApi, transcriptApprovalErrorMessage } from './transcript-approval-api';
import { TranscriptionSession } from './transcription-session';

describe('TranscriptApprovalApi — intercepted HTTP only', () => {
  const endpoint = 'http://localhost:8080/api/hubspot/sync';
  const payload = { dealCode: ' DEAL-5 ', text: '  Testo corretto.\nSeconda riga.  ' };
  let api: TranscriptApprovalApi;
  let http: HttpTestingController;
  let session: TranscriptionSession;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080/api/' },
      ],
    });
    api = TestBed.inject(TranscriptApprovalApi);
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(TranscriptionSession);
    session.setTranscript({ id: 'job-77', text: 'Testo originale.' });
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('should use the actual endpoint and exactly the JSON fields supplied by the caller', () => {
    const next = vi.fn();
    api.approve('job-77', payload).subscribe(next);
    const request = http.expectOne(endpoint);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    expect(Object.keys(request.request.body).sort()).toEqual(['dealCode', 'text']);
    expect(request.request.detectContentTypeHeader()).toBe('application/json');
    expect(session.approvalInProgress()).toBe('job-77');
    request.flush({ status: 'success', dealId: 'deal-123', noteId: 'note-456' });

    expect(next).toHaveBeenCalledWith({
      status: 'success',
      dealId: 'deal-123',
      noteId: 'note-456',
    });
    expect(session.decisionFor('job-77')).toEqual({
      kind: 'approved',
      response: { status: 'success', dealId: 'deal-123', noteId: 'note-456' },
    });
    expect(session.approvalInProgress()).toBeNull();
    expect(session.transcript()?.text).toBe(payload.text);
  });

  it('should share one request and reject concurrent approval attempts', () => {
    const approval = api.approve('job-77', payload);
    approval.subscribe();
    approval.subscribe();
    const request = http.expectOne(endpoint);
    const error = vi.fn();

    api.approve('job-77', payload).subscribe({ error });
    expect(error).toHaveBeenCalledTimes(1);
    http.expectNone(endpoint);
    expect(session.discard('job-77')).toBe(false);
    request.flush({ status: 'success', noteId: 'note-456' });
  });

  it('should prevent another approval after success even if the session is reloaded in memory', () => {
    api.approve('job-77', payload).subscribe();
    http.expectOne(endpoint).flush({ status: 'success', noteId: 'note-456' });
    session.clear();
    session.setTranscript({ id: 'job-77', text: 'Stessa trascrizione.' });

    const error = vi.fn();
    api.approve('job-77', payload).subscribe({ error });
    expect(error).toHaveBeenCalledTimes(1);
    expect(session.canApprove('job-77')).toBe(false);
    http.expectNone(() => true);
  });

  it.each([
    { dealCode: '', text: 'Testo valido.' },
    { dealCode: '   ', text: 'Testo valido.' },
    { dealCode: 'DEAL-5', text: '' },
    { dealCode: 'DEAL-5', text: ' \n\t ' },
  ])('should refuse invalid payloads before any request: %j', (invalid) => {
    const error = vi.fn();
    api.approve('job-77', invalid).subscribe({ error });
    expect(error).toHaveBeenCalledTimes(1);
    expect(session.approvalInProgress()).toBeNull();
    http.expectNone(() => true);
  });

  it('should refuse a demo or unrelated document even when a real session exists', () => {
    const error = vi.fn();
    api.approve('demo-transcript-scrum-53', payload).subscribe({ error });
    expect(error).toHaveBeenCalledTimes(1);
    expect(session.canApprove('job-77')).toBe(true);
    http.expectNone(() => true);
  });

  it('should refuse a request without an active session', () => {
    session.clear();
    const error = vi.fn();
    api.approve('job-77', payload).subscribe({ error });
    expect(error).toHaveBeenCalledTimes(1);
    http.expectNone(() => true);
  });

  it('should discard locally, erase the transcript and prevent reviving the same document', () => {
    expect(session.discard('job-77')).toBe(true);
    expect(session.transcript()).toBeNull();
    expect(session.decisionFor('job-77')).toEqual({ kind: 'discarded' });
    session.setTranscript({ id: 'job-77', text: 'Da non ripristinare.' });
    expect(session.transcript()).toBeNull();
    const error = vi.fn();
    api.approve('job-77', payload).subscribe({ error });
    expect(error).toHaveBeenCalledTimes(1);
    http.expectNone(() => true);

    session.setTranscript({ id: 'new-job', text: 'Nuova trascrizione.' });
    expect(session.canApprove('new-job')).toBe(true);
  });

  it.each([null, {}, { noteId: 'note-456' }, { status: 'error' }])(
    'should explain an incomplete response and keep manual retry available: %j',
    (response) => {
      const error = vi.fn();
      api.approve('job-77', payload).subscribe({ error });
      http.expectOne(endpoint).flush(response);
      expect(error).toHaveBeenCalledWith(
        expect.objectContaining({ message: expect.stringContaining('risposta incompleta') }),
      );
      expect(session.canApprove('job-77')).toBe(true);
      expect(session.transcript()?.text).toBe('Testo originale.');
      vi.advanceTimersByTime(120_000);
      http.expectNone(() => true);
    },
  );

  it('should accept explicit success without noteId and still lock future approval', () => {
    const next = vi.fn();
    api.approve('job-77', payload).subscribe(next);
    http.expectOne(endpoint).flush({ status: 'success' });
    expect(next).toHaveBeenCalledWith({ status: 'success', noteId: undefined, dealId: undefined });
    expect(session.canApprove('job-77')).toBe(false);
  });

  it('should explain HTTP 400 and perform no automatic retries', () => {
    const error = vi.fn();
    api.approve('job-77', payload).subscribe({ error });
    http
      .expectOne(endpoint)
      .flush(
        { error: 'No deal found with code: DEAL-5' },
        { status: 400, statusText: 'Bad Request' },
      );
    expect(transcriptApprovalErrorMessage(error.mock.calls[0][0])).toContain('No deal found');
    expect(session.canApprove('job-77')).toBe(true);
    vi.advanceTimersByTime(120_000);
    http.expectNone(() => true);
  });

  it('should report network failures without claiming that no note was created', () => {
    const error = vi.fn();
    api.approve('job-77', payload).subscribe({ error });
    http.expectOne(endpoint).error(new ProgressEvent('error'));
    const message = transcriptApprovalErrorMessage(error.mock.calls[0][0]);
    expect(message).toContain('Errore di rete');
    expect(message).toContain('verifica la nota');
    expect(session.canApprove('job-77')).toBe(true);
    http.expectNone(() => true);
  });

  it('should time out without retrying or assuming an unsuccessful external side effect', () => {
    const error = vi.fn();
    api.approve('job-77', payload).subscribe({ error });
    const request = http.expectOne(endpoint);
    vi.advanceTimersByTime(60_000);
    expect(request.cancelled).toBe(true);
    expect(error).toHaveBeenCalledWith(expect.any(TimeoutError));
    expect(transcriptApprovalErrorMessage(error.mock.calls[0][0])).toContain('non è confermato');
    expect(session.approvalInProgress()).toBeNull();
    vi.advanceTimersByTime(120_000);
    http.expectNone(() => true);
  });

  it('should retain the pending operation and success after the UI unsubscribes', () => {
    const subscription = api.approve('job-77', payload).subscribe();
    const request = http.expectOne(endpoint);
    subscription.unsubscribe();
    expect(request.cancelled).toBe(false);
    expect(session.canApprove('job-77')).toBe(false);
    request.flush({ status: 'success', noteId: 'note-after-navigation' });
    expect(session.decisionFor('job-77')).toEqual({
      kind: 'approved',
      response: { status: 'success', noteId: 'note-after-navigation', dealId: undefined },
    });
    expect(session.approvalInProgress()).toBeNull();
  });
});
