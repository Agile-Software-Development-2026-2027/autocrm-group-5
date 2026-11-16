import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TimeoutError } from 'rxjs';
import { vi } from 'vitest';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import { TranscriptionApi } from './transcription-api';
import { TRANSCRIPTION_TIMING } from './transcription-timing';

describe('TranscriptionApi with an HTTP mock', () => {
  const baseUrl = 'http://localhost:8080/api';
  const statusUrl = `${baseUrl}/transcriptions/job-66`;
  let api: TranscriptionApi;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: `${baseUrl}/` },
        {
          provide: TRANSCRIPTION_TIMING,
          useValue: {
            pollIntervalMs: 10,
            maxPollingMs: 100,
            requestTimeoutMs: 50,
            uploadTimeoutMs: 120,
          },
        },
      ],
    });
    api = TestBed.inject(TranscriptionApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('should POST multipart field file without setting its Content-Type manually', () => {
    const file = new File(['audio'], 'registrazione.wav', { type: 'audio/wav' });
    const next = vi.fn();
    api.upload(file).subscribe(next);

    const request = http.expectOne(`${baseUrl}/transcriptions/upload`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.has('Content-Type')).toBe(false);
    expect(request.request.body).toBeInstanceOf(FormData);
    const uploadedFile = request.request.body.get('file') as File;
    expect(uploadedFile.name).toBe(file.name);
    expect(uploadedFile.size).toBe(file.size);
    expect(uploadedFile.type).toBe(file.type);

    request.flush(
      { jobId: 'job-66', status: 'processing' },
      { status: 202, statusText: 'Accepted' },
    );
    expect(next).toHaveBeenCalledWith({ jobId: 'job-66', status: 'processing' });
  });

  it.each([null, {}, { jobId: '', status: 'processing' }, { jobId: 'job-66', status: 'done' }])(
    'should reject an invalid upload response: %j',
    (body) => {
      const error = vi.fn();
      api.upload(new File(['audio'], 'audio.mp3')).subscribe({ error });
      http.expectOne(`${baseUrl}/transcriptions/upload`).flush(body);
      expect(error).toHaveBeenCalledWith(
        expect.objectContaining({ message: expect.stringContaining('non valida') }),
      );
    },
  );

  it('should encode the job ID as a single URL segment', () => {
    const next = vi.fn();
    api.getStatus('job/id').subscribe(next);
    const request = http.expectOne(`${baseUrl}/transcriptions/job%2Fid`);
    expect(request.request.method).toBe('GET');
    request.flush({ jobId: 'job/id', status: 'processing', transcript: null });
    expect(next).toHaveBeenCalledWith({ jobId: 'job/id', status: 'processing', transcript: null });
  });

  it('should poll processing to done and stop after returning the real response text', () => {
    const next = vi.fn();
    const complete = vi.fn();
    api.waitForTranscript('job-66').subscribe({ next, complete });

    http.expectOne(statusUrl).flush({ jobId: 'job-66', status: 'processing', transcript: null });
    vi.advanceTimersByTime(9);
    http.expectNone(statusUrl);
    vi.advanceTimersByTime(1);
    http
      .expectOne(statusUrl)
      .flush({ jobId: 'job-66', status: 'done', transcript: 'Testo dal backend.' });

    expect(next).toHaveBeenCalledExactlyOnceWith({ id: 'job-66', text: 'Testo dal backend.' });
    expect(complete).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(500);
    http.expectNone(statusUrl);
  });

  it('should not overlap status requests while a response is pending', () => {
    const subscription = api.waitForTranscript('job-66').subscribe();
    const first = http.expectOne(statusUrl);
    vi.advanceTimersByTime(25);
    http.expectNone(statusUrl);

    first.flush({ jobId: 'job-66', status: 'processing', transcript: null });
    vi.advanceTimersByTime(10);
    const second = http.expectOne(statusUrl);
    subscription.unsubscribe();
    expect(second.cancelled).toBe(true);
  });

  it('should report a failed job even when the backend exposes no errorMessage', () => {
    const error = vi.fn();
    api.waitForTranscript('job-66').subscribe({ error });
    http.expectOne(statusUrl).flush({ jobId: 'job-66', status: 'failed', transcript: null });

    expect(error).toHaveBeenCalledWith(
      expect.objectContaining({ message: expect.stringContaining('non è riuscita') }),
    );
    vi.advanceTimersByTime(500);
    http.expectNone(statusUrl);
  });

  it.each([
    { jobId: 'job-66', status: 'done', transcript: null },
    { jobId: 'job-66', status: 'unexpected', transcript: null },
    { jobId: 'different-job', status: 'done', transcript: 'Testo errato.' },
  ])('should reject an invalid status response: %j', (body) => {
    const error = vi.fn();
    api.waitForTranscript('job-66').subscribe({ error });
    http.expectOne(statusUrl).flush(body);
    expect(error).toHaveBeenCalledWith(
      expect.objectContaining({ message: expect.stringContaining('non valida') }),
    );
  });

  it('should accept a completed empty transcript without treating it as missing data', () => {
    const next = vi.fn();
    api.waitForTranscript('job-66').subscribe(next);
    http.expectOne(statusUrl).flush({ jobId: 'job-66', status: 'done', transcript: '' });
    expect(next).toHaveBeenCalledWith({ id: 'job-66', text: '' });
  });

  it('should terminate a job that keeps processing beyond the overall deadline', () => {
    const error = vi.fn();
    api.waitForTranscript('job-66').subscribe({ error });
    for (let index = 0; index < 10; index++) {
      http.expectOne(statusUrl).flush({ jobId: 'job-66', status: 'processing', transcript: null });
      vi.advanceTimersByTime(10);
    }
    expect(error).toHaveBeenCalledWith(expect.any(TimeoutError));
    http.expectNone(statusUrl);
  });

  it('should time out a status request that never returns', () => {
    const error = vi.fn();
    api.getStatus('job-66').subscribe({ error });
    const request = http.expectOne(statusUrl);
    vi.advanceTimersByTime(50);
    expect(request.cancelled).toBe(true);
    expect(error).toHaveBeenCalledWith(expect.any(TimeoutError));
  });

  it('should time out an upload that never returns', () => {
    const error = vi.fn();
    api.upload(new File(['audio'], 'audio.mp3')).subscribe({ error });
    const request = http.expectOne(`${baseUrl}/transcriptions/upload`);
    vi.advanceTimersByTime(120);
    expect(request.cancelled).toBe(true);
    expect(error).toHaveBeenCalledWith(expect.any(TimeoutError));
  });

  it('should cancel an active request when unsubscribed', () => {
    const subscription = api.waitForTranscript('job-66').subscribe();
    const request = http.expectOne(statusUrl);
    subscription.unsubscribe();
    expect(request.cancelled).toBe(true);
    vi.advanceTimersByTime(500);
    http.expectNone(statusUrl);
  });

  it('should cancel the next poll timer when unsubscribed', () => {
    const subscription = api.waitForTranscript('job-66').subscribe();
    http.expectOne(statusUrl).flush({ jobId: 'job-66', status: 'processing', transcript: null });
    subscription.unsubscribe();
    vi.advanceTimersByTime(500);
    http.expectNone(statusUrl);
  });
});
