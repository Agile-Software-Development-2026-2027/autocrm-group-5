import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';
import { createAppRoutes } from '../../../../app.routes';
import { API_BASE_URL } from '../../../../core/config/api-base-url';
import { TranscriptApprovalApi } from '../../data-access/transcript-approval-api';
import { TranscriptionSession } from '../../data-access/transcription-session';
import { TranscriptReviewPage } from './transcript-review-page';

describe('SCRUM-77 Review actions — mocked HTTP only', () => {
  const endpoint = '/api/hubspot/sync';
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let session: TranscriptionSession;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(createAppRoutes(true)),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(TranscriptionSession);
    session.setTranscript({ id: 'job-77', text: 'Testo originale ricevuto dal backend.' });
    harness = await RouterTestingHarness.create('/transcript-review');
  });

  afterEach(() => {
    harness.fixture.destroy();
    http.verify();
  });

  function element(): HTMLElement {
    return harness.routeNativeElement!;
  }

  function textarea(): HTMLTextAreaElement {
    return element().querySelector('textarea')!;
  }

  function dealInput(): HTMLInputElement {
    return element().querySelector('#review-deal-code')!;
  }

  function approveButton(): HTMLButtonElement {
    return element().querySelector('button[type="submit"]')!;
  }

  function discardButton(): HTMLButtonElement {
    return Array.from(element().querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === 'Discard',
    )!;
  }

  function setDealCode(value: string): void {
    dealInput().value = value;
    dealInput().dispatchEvent(new Event('input', { bubbles: true }));
    harness.detectChanges();
  }

  function editText(value: string): void {
    textarea().value = value;
    textarea().dispatchEvent(new Event('input', { bubbles: true }));
    harness.detectChanges();
  }

  function submit(): void {
    element()
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    harness.detectChanges();
  }

  it('should display a labelled Deal Code field and both requested actions', () => {
    expect(element().querySelector('label[for="review-deal-code"]')?.textContent).toBe('Deal Code');
    expect(dealInput().getAttribute('aria-describedby')).toContain('deal-code-help');
    expect(approveButton().textContent?.trim()).toBe('Approve and send to HubSpot');
    expect(discardButton()).toBeDefined();
    http.expectNone(() => true);
  });

  it('should reset Deal Code and validation when another document is explicitly selected', () => {
    setDealCode('DEAL-PREVIOUS');
    editText('');
    submit();
    expect(element().querySelector('#transcript-validation-error')).not.toBeNull();

    session.setTranscript({ id: 'next-job', text: 'Testo di un altro documento.' });
    harness.detectChanges();

    expect(textarea().value).toBe('Testo di un altro documento.');
    expect(dealInput().value).toBe('');
    expect(element().querySelector('[role="alert"]')).toBeNull();
    expect(approveButton().disabled).toBe(false);
    http.expectNone(() => true);
  });

  it('should accept a new source after an external local discard without reusing the old decision', () => {
    expect(session.discard('job-77')).toBe(true);
    session.setTranscript({ id: 'next-job', text: 'Nuovo documento dopo Discard.' });
    harness.detectChanges();

    expect(textarea().value).toBe('Nuovo documento dopo Discard.');
    expect(dealInput().value).toBe('');
    expect(approveButton().disabled).toBe(false);
    http.expectNone(() => true);
  });

  it.each(['', '   '])(
    'should block empty or whitespace Deal Code with its own message: %j',
    (dealCode) => {
      setDealCode(dealCode);
      submit();
      expect(element().querySelector('#deal-code-error')?.textContent).toContain(
        'Inserisci il Deal Code',
      );
      expect(dealInput().getAttribute('aria-invalid')).toBe('true');
      expect(element().querySelector('#transcript-validation-error')).toBeNull();
      http.expectNone(() => true);
    },
  );

  it.each(['', ' \n\t '])(
    'should block empty or whitespace transcript with its own message: %j',
    (text) => {
      setDealCode('DEAL-5');
      editText(text);
      submit();
      expect(element().querySelector('#transcript-validation-error')?.textContent).toContain(
        'La trascrizione è vuota',
      );
      expect(element().querySelector('#deal-code-error')).toBeNull();
      http.expectNone(() => true);
    },
  );

  it('should send exactly the edited transcript and entered Deal Code as JSON', () => {
    const text = '  Testo corretto dall’utente.\nNuova riga.  ';
    setDealCode(' DEAL-5 ');
    editText(text);
    submit();
    const request = http.expectOne(endpoint);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ dealCode: ' DEAL-5 ', text });
    request.flush({ status: 'success', dealId: 'deal-123', noteId: 'note-456' });
    harness.detectChanges();
    expect(element().querySelector('.success')?.textContent).toContain('note-456');
    expect(approveButton().disabled).toBe(true);
    expect(discardButton().disabled).toBe(false);
    expect(textarea().readOnly).toBe(true);
  });

  it('should allow clearing the local session after success without undoing the remote note', () => {
    setDealCode('DEAL-5');
    submit();
    http.expectOne(endpoint).flush({ status: 'success', noteId: 'note-456' });
    harness.detectChanges();
    expect(approveButton().disabled).toBe(true);
    expect(discardButton().disabled).toBe(false);

    discardButton().click();
    harness.detectChanges();
    expect(session.transcript()).toBeNull();
    expect(session.decisionFor('job-77')).toEqual({ kind: 'discarded' });
    expect(element().querySelector('textarea')).toBeNull();
    http.expectNone(() => true);
  });

  it('should block duplicate submits, editing and discard while approval is pending', () => {
    setDealCode('DEAL-5');
    submit();
    const request = http.expectOne(endpoint);
    expect(element().textContent).toContain('Approvazione in corso');
    expect(approveButton().disabled).toBe(true);
    expect(discardButton().disabled).toBe(true);
    expect(dealInput().disabled).toBe(true);
    expect(textarea().readOnly).toBe(true);
    submit();
    discardButton().click();
    http.expectNone(endpoint);
    expect(session.transcript()?.id).toBe('job-77');
    request.flush({ status: 'success', noteId: 'note-456' });
  });

  it('should preserve the success lock when leaving and reopening the same document', async () => {
    setDealCode('DEAL-5');
    editText('Correzione effettivamente approvata.');
    submit();
    http.expectOne(endpoint).flush({ status: 'success', noteId: 'note-456' });
    await harness.navigateByUrl('/audio-upload');
    await harness.navigateByUrl('/transcript-review');
    expect(element().querySelector('.success')?.textContent).toContain('note-456');
    expect(textarea().value).toBe('Correzione effettivamente approvata.');
    expect(approveButton().disabled).toBe(true);
    submit();
    http.expectNone(() => true);
  });

  it('should show success without a note reference and prevent reapproval', () => {
    setDealCode('DEAL-5');
    submit();
    http.expectOne(endpoint).flush({ status: 'success' });
    harness.detectChanges();
    expect(element().querySelector('.success')?.textContent).toContain('inviata con successo');
    expect(element().querySelector('.success')?.textContent).toContain(
      'non ha restituito il riferimento',
    );
    expect(approveButton().disabled).toBe(true);
    submit();
    http.expectNone(() => true);
  });

  it('should display HTTP 400 details while preserving corrections and Deal Code', () => {
    setDealCode('DEAL-5');
    editText('Correzione da conservare.');
    submit();
    http
      .expectOne(endpoint)
      .flush(
        { error: 'No deal found with code: DEAL-5' },
        { status: 400, statusText: 'Bad Request' },
      );
    harness.detectChanges();
    expect(element().querySelector('[role="alert"]')?.textContent).toContain('No deal found');
    expect(textarea().value).toBe('Correzione da conservare.');
    expect(dealInput().value).toBe('DEAL-5');
    expect(textarea().readOnly).toBe(false);
    expect(approveButton().disabled).toBe(false);
    http.expectNone(() => true);
  });

  it('should explain network failure and allow a deliberate retry with the retained draft', () => {
    setDealCode('DEAL-5');
    editText('Bozza conservata dopo errore.');
    submit();
    http.expectOne(endpoint).error(new ProgressEvent('error'));
    harness.detectChanges();
    expect(element().querySelector('[role="alert"]')?.textContent).toContain('Errore di rete');
    expect(dealInput().value).toBe('DEAL-5');
    expect(textarea().value).toBe('Bozza conservata dopo errore.');
    http.expectNone(() => true);

    submit();
    const retry = http.expectOne(endpoint);
    expect(retry.request.body).toEqual({
      dealCode: 'DEAL-5',
      text: 'Bozza conservata dopo errore.',
    });
    retry.flush({ status: 'success', noteId: 'note-retry' });
    harness.detectChanges();
    expect(element().querySelector('.success')?.textContent).toContain('note-retry');
  });

  it('should explain an incomplete response without losing user data', () => {
    setDealCode('DEAL-5');
    editText('Bozza corretta.');
    submit();
    http.expectOne(endpoint).flush({});
    harness.detectChanges();
    expect(element().querySelector('[role="alert"]')?.textContent).toContain('risposta incompleta');
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      'controlla che la nota',
    );
    expect(textarea().value).toBe('Bozza corretta.');
    expect(dealInput().value).toBe('DEAL-5');
    expect(approveButton().disabled).toBe(false);
  });

  it('should explain a server error and retain the form for correction', () => {
    setDealCode('DEAL-5');
    submit();
    http
      .expectOne(endpoint)
      .flush({ error: 'Service unavailable.' }, { status: 500, statusText: 'Server Error' });
    harness.detectChanges();
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      'Service unavailable.',
    );
    expect(dealInput().value).toBe('DEAL-5');
    expect(approveButton().disabled).toBe(false);
    http.expectNone(() => true);
  });

  it('should discard the session and draft without HTTP and allow returning to Upload', async () => {
    setDealCode('DEAL-5');
    editText('Bozza da scartare.');
    const page: TranscriptReviewPage = harness.routeDebugElement!.query(
      By.directive(TranscriptReviewPage),
    ).componentInstance;
    discardButton().click();
    harness.detectChanges();

    expect(session.transcript()).toBeNull();
    expect(page.draftText()).toBeNull();
    expect(element().querySelector('textarea')).toBeNull();
    expect(element().querySelector('#review-deal-code')).toBeNull();
    expect(element().textContent).toContain('Trascrizione scartata');
    http.expectNone(() => true);

    element().querySelector<HTMLAnchorElement>('a[href="/audio-upload"]')!.click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/audio-upload');
    http.expectNone(() => true);
  });

  it('should block a discarded document and allow a new session to start cleanly', async () => {
    discardButton().click();
    harness.detectChanges();
    const error = vi.fn();
    TestBed.inject(TranscriptApprovalApi)
      .approve('job-77', { dealCode: 'DEAL-5', text: 'Testo precedente.' })
      .subscribe({ error });
    expect(error).toHaveBeenCalledTimes(1);
    session.setTranscript({ id: 'job-77', text: 'Da non ripristinare.' });
    expect(session.transcript()).toBeNull();
    http.expectNone(() => true);

    await harness.navigateByUrl('/audio-upload');
    session.setTranscript({ id: 'new-job', text: 'Nuova trascrizione.' });
    await harness.navigateByUrl('/transcript-review');
    expect(textarea().value).toBe('Nuova trascrizione.');
    expect(dealInput().value).toBe('');
    expect(approveButton().disabled).toBe(false);
  });

  it('should never offer or send approval from the demo even with a real session present', async () => {
    await harness.navigateByUrl('/transcript-review/demo');
    expect(element().textContent).toContain('Modalità dimostrativa locale');
    expect(element().querySelector('app-transcript-review-actions')).toBeNull();
    expect(element().querySelector('#review-deal-code')).toBeNull();
    expect(element().textContent).not.toContain('Approve and send to HubSpot');
    editText('Modifica dimostrativa.');
    const demoButtons = Array.from(element().querySelectorAll('button'));
    demoButtons.find((button) => button.textContent?.includes('Simula aggiornamento'))!.click();
    demoButtons.find((button) => button.textContent?.includes('Simula errore'))!.click();
    harness.detectChanges();
    expect(textarea().value).toBe('Modifica dimostrativa.');
    expect(session.transcript()?.id).toBe('job-77');
    const error = vi.fn();
    TestBed.inject(TranscriptApprovalApi)
      .approve('demo-transcript-scrum-53', { dealCode: 'DEAL-5', text: 'Demo.' })
      .subscribe({ error });
    expect(error).toHaveBeenCalledTimes(1);
    http.expectNone(() => true);
  });

  it('should not cancel or duplicate an approval when navigating away during the request', async () => {
    setDealCode('DEAL-5');
    editText('Correzione inviata prima del cambio pagina.');
    submit();
    const request = http.expectOne(endpoint);
    await TestBed.inject(Router).navigateByUrl('/audio-upload');
    harness.detectChanges();
    expect(request.cancelled).toBe(false);

    await TestBed.inject(Router).navigateByUrl('/transcript-review');
    harness.detectChanges();
    expect(approveButton().disabled).toBe(true);
    submit();
    http.expectNone(endpoint);

    request.flush({ status: 'success', noteId: 'note-after-navigation' });
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(element().querySelector('.success')?.textContent).toContain('note-after-navigation');
    expect(textarea().value).toBe('Correzione inviata prima del cambio pagina.');
    expect(approveButton().disabled).toBe(true);
  });
});
