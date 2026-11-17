import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';
import { createAppRoutes } from '../../../../app.routes';
import { API_BASE_URL } from '../../../../core/config/api-base-url';
import { TranscriptionSession } from '../../data-access/transcription-session';
import { TRANSCRIPTION_TIMING } from '../../data-access/transcription-timing';

describe('Upload → processing → Review with mocked HTTP', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  beforeEach(async () => {
    // RxJS usa setInterval; i timer di rendering Angular rimangono reali.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(createAppRoutes(false)),
        { provide: API_BASE_URL, useValue: '/api' },
        {
          provide: TRANSCRIPTION_TIMING,
          useValue: {
            pollIntervalMs: 10,
            maxPollingMs: 1000,
            requestTimeoutMs: 500,
            uploadTimeoutMs: 500,
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create('/audio-upload');
  });

  afterEach(() => {
    harness.fixture.destroy();
    http.verify();
    vi.useRealTimers();
  });

  function button(): HTMLButtonElement {
    return harness.routeNativeElement!.querySelector('button')!;
  }

  function selectFile(name = 'registrazione.wav', type = 'audio/wav'): void {
    const input =
      harness.routeNativeElement!.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [new File(['audio'], name, { type })],
    });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    harness.detectChanges();
  }

  function startJob(): void {
    selectFile();
    button().click();
    harness.detectChanges();
    http
      .expectOne('/api/transcriptions/upload')
      .flush({ jobId: 'job-66', status: 'processing' }, { status: 202, statusText: 'Accepted' });
    harness.detectChanges();
  }

  function expectUploadError(text: string): void {
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')?.textContent).toContain(
      text,
    );
    expect(TestBed.inject(Router).url).toBe('/audio-upload');
    expect(button().disabled).toBe(false);
  }

  it('should not send HTTP before a valid file and an explicit upload click', () => {
    expect(button().disabled).toBe(true);
    button().click();
    http.expectNone(() => true);

    selectFile();
    expect(button().disabled).toBe(false);
    http.expectNone(() => true);
  });

  it('should show processing and navigate to the real editor with backend text', async () => {
    startJob();
    expect(harness.routeNativeElement!.textContent).toContain('Trascrizione in corso');
    expect(button().disabled).toBe(true);
    expect(
      [...harness.routeNativeElement!.querySelectorAll('[role="status"], [role="alert"]')].every(
        (region) => !region.closest('[aria-busy="true"]'),
      ),
    ).toBe(true);
    expect(harness.routeNativeElement!.querySelector<HTMLInputElement>('input')!.disabled).toBe(
      true,
    );

    http
      .expectOne('/api/transcriptions/job-66')
      .flush({ jobId: 'job-66', status: 'processing', transcript: null });
    vi.advanceTimersByTime(10);
    http.expectOne('/api/transcriptions/job-66').flush({
      jobId: 'job-66',
      status: 'done',
      transcript: 'Trascrizione restituita dal backend.',
    });
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/transcript-review');
    const textarea = harness.routeNativeElement!.querySelector('textarea')!;
    expect(textarea.value).toBe('Trascrizione restituita dal backend.');
    expect(TestBed.inject(TranscriptionSession).transcript()).toEqual({
      id: 'job-66',
      text: 'Trascrizione restituita dal backend.',
    });

    textarea.value = 'Correzione nella Review reale.';
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
    harness.detectChanges();
    expect(textarea.value).toBe('Correzione nella Review reale.');
    expect(harness.routeNativeElement!.querySelector('[role="status"]')?.textContent).toContain(
      'modifiche sono conservate',
    );
    vi.advanceTimersByTime(100);
    http.expectNone('/api/transcriptions/job-66');
  });

  it('should let the backend reject formats accepted by the MIME-based picker', () => {
    selectFile('registrazione.aiff', 'audio/aiff');
    button().click();
    http
      .expectOne('/api/transcriptions/upload')
      .flush(
        { error: 'Invalid file format or file size exceeded.' },
        { status: 400, statusText: 'Bad Request' },
      );
    expectUploadError('formato non supportato');
    expect(TestBed.inject(TranscriptionSession).transcript()).toBeNull();
    http.expectNone('/api/transcriptions/job-66');
  });

  it('should display a readable network failure', () => {
    selectFile();
    button().click();
    http.expectOne('/api/transcriptions/upload').error(new ProgressEvent('error'));
    expectUploadError('Impossibile raggiungere il backend');
  });

  it('should stop on a failed transcription without navigating', () => {
    startJob();
    http
      .expectOne('/api/transcriptions/job-66')
      .flush({ jobId: 'job-66', status: 'failed', transcript: null });
    expectUploadError('La trascrizione non è riuscita');
    vi.advanceTimersByTime(100);
    http.expectNone('/api/transcriptions/job-66');
  });

  it('should display a missing-job error and stop polling', () => {
    startJob();
    http
      .expectOne('/api/transcriptions/job-66')
      .flush({ error: 'Job ID not found.' }, { status: 404, statusText: 'Not Found' });
    expectUploadError('non è stata trovata');
    vi.advanceTimersByTime(100);
    http.expectNone('/api/transcriptions/job-66');
  });

  it('should reject a done response without a transcript instead of showing invented text', () => {
    startJob();
    http
      .expectOne('/api/transcriptions/job-66')
      .flush({ jobId: 'job-66', status: 'done', transcript: null });
    expectUploadError('risposta di trascrizione non valida');
    expect(TestBed.inject(TranscriptionSession).transcript()).toBeNull();
  });

  it('should prevent duplicate uploads while busy and clear stale session data', () => {
    TestBed.inject(TranscriptionSession).setTranscript({
      id: 'old-job',
      text: 'Vecchio risultato.',
    });
    selectFile();
    const uploadButton = button();
    uploadButton.click();
    harness.detectChanges();
    const request = http.expectOne('/api/transcriptions/upload');

    expect(TestBed.inject(TranscriptionSession).transcript()).toBeNull();
    expect(uploadButton.disabled).toBe(true);
    expect(harness.routeNativeElement!.textContent).toContain('Caricamento del file in corso');
    uploadButton.dispatchEvent(new Event('click', { bubbles: true }));
    http.expectNone('/api/transcriptions/upload');
    request.flush({}, { status: 500, statusText: 'Server Error' });
    expectUploadError('Riprova più tardi');
  });

  it('should allow a fresh upload after a backend error', async () => {
    selectFile();
    button().click();
    http
      .expectOne('/api/transcriptions/upload')
      .flush({}, { status: 500, statusText: 'Server Error' });
    expectUploadError('Riprova più tardi');

    button().click();
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
    http
      .expectOne('/api/transcriptions/upload')
      .flush({ jobId: 'retry-job', status: 'processing' }, { status: 202, statusText: 'Accepted' });
    http
      .expectOne('/api/transcriptions/retry-job')
      .flush({ jobId: 'retry-job', status: 'done', transcript: 'Nuovo risultato.' });
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/transcript-review');
    expect(harness.routeNativeElement!.querySelector('textarea')?.value).toBe('Nuovo risultato.');
  });

  it('should cancel pending HTTP and polling when leaving Upload', async () => {
    startJob();
    const request = http.expectOne('/api/transcriptions/job-66');

    await harness.navigateByUrl('/transcript-review');

    expect(request.cancelled).toBe(true);
    vi.advanceTimersByTime(2000);
    http.expectNone('/api/transcriptions/job-66');
    expect(harness.routeNativeElement!.querySelector('textarea')).toBeNull();
  });
});
