import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranscriptSource } from '../../models/transcript-source';
import { TranscriptReviewPage } from './transcript-review-page';

describe('TranscriptReviewPage', () => {
  let fixture: ComponentFixture<TranscriptReviewPage>;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TranscriptReviewPage],
    }).compileComponents();

    fixture = TestBed.createComponent(TranscriptReviewPage);
    fixture.detectChanges();
    element = fixture.nativeElement;
  });

  function setTranscript(transcript: TranscriptSource | null): void {
    fixture.componentRef.setInput('transcript', transcript);
    fixture.detectChanges();
  }

  function textarea(): HTMLTextAreaElement {
    return element.querySelector<HTMLTextAreaElement>('textarea')!;
  }

  function edit(text: string): void {
    textarea().value = text;
    textarea().dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
  }

  it('should render the normal review page without backend or demo data', () => {
    expect(element.querySelector('h1')?.textContent).toBe('Transcript Review');
    expect(element.querySelector('h2')?.textContent).toBe('Nessuna trascrizione disponibile');
    expect(element.querySelector('app-transcript-editor')).not.toBeNull();
    expect(element.querySelector('textarea')).toBeNull();
    expect(fixture.componentInstance.draftText()).toBeNull();
  });

  it('should render the supplied transcript and retain its identity', () => {
    setTranscript({ id: 'job-53', text: 'Trascrizione ricevuta.' });

    expect(textarea().value).toBe('Trascrizione ricevuta.');
    expect(fixture.componentInstance.draftText()).toBe('Trascrizione ricevuta.');
    expect(fixture.componentInstance.transcriptId()).toBe('job-53');
    expect(fixture.componentInstance.isDirty()).toBe(false);
  });

  it('should retain editor corrections in the page for future review actions', () => {
    setTranscript({ id: 'job-53', text: 'Testo originale.' });
    edit('Testo corretto.\nNuova riga.');
    fixture.detectChanges();

    expect(fixture.componentInstance.draftText()).toBe('Testo corretto.\nNuova riga.');
    expect(fixture.componentInstance.isDirty()).toBe(true);
    expect(textarea().value).toBe('Testo corretto.\nNuova riga.');
    expect(element.querySelector('[role="status"]')?.textContent).toContain(
      'modifiche sono conservate',
    );
  });

  it('should preserve edits when the parent resends the same transcript as a new object', () => {
    setTranscript({ id: 'job-53', text: 'Testo originale.' });
    edit('Correzioni locali.');

    setTranscript({ id: 'job-53', text: 'Testo originale.' });

    expect(textarea().value).toBe('Correzioni locali.');
    expect(fixture.componentInstance.draftText()).toBe('Correzioni locali.');
    expect(fixture.componentInstance.isDirty()).toBe(true);
  });

  it('should preserve edits when an updated source arrives for the same transcript', () => {
    setTranscript({ id: 'job-53', text: 'Testo originale.' });
    edit('Correzioni locali.');

    setTranscript({ id: 'job-53', text: 'Nuovo testo dalla sorgente.' });

    expect(textarea().value).toBe('Correzioni locali.');
    expect(fixture.componentInstance.isDirty()).toBe(true);
  });

  it('should apply updated source text when the draft has not been modified', () => {
    setTranscript({ id: 'job-53', text: 'Testo originale.' });

    setTranscript({ id: 'job-53', text: 'Aggiornamento della sorgente.' });

    expect(textarea().value).toBe('Aggiornamento della sorgente.');
    expect(fixture.componentInstance.draftText()).toBe('Aggiornamento della sorgente.');
    expect(fixture.componentInstance.isDirty()).toBe(false);
  });

  it('should start a fresh draft only when a different transcript is explicitly supplied', () => {
    setTranscript({ id: 'job-53', text: 'Prima trascrizione.' });
    edit('Correzioni alla prima trascrizione.');

    setTranscript({ id: 'job-66', text: 'Seconda trascrizione.' });

    expect(textarea().value).toBe('Seconda trascrizione.');
    expect(fixture.componentInstance.transcriptId()).toBe('job-66');
    expect(fixture.componentInstance.isDirty()).toBe(false);
  });

  it('should keep a modified draft if the parent temporarily supplies no source', () => {
    setTranscript({ id: 'job-53', text: 'Testo originale.' });
    edit('Bozza da conservare.');

    setTranscript(null);

    expect(fixture.componentInstance.draftText()).toBe('Bozza da conservare.');
    expect(fixture.componentInstance.transcriptId()).toBe('job-53');
    expect(textarea().value).toBe('Bozza da conservare.');
  });

  it('should show the empty state for an absent source when the draft is unmodified', () => {
    setTranscript({ id: 'job-53', text: 'Testo originale.' });

    setTranscript(null);

    expect(fixture.componentInstance.draftText()).toBeNull();
    expect(element.querySelector('textarea')).toBeNull();
    expect(element.querySelector('h2')?.textContent).toBe('Nessuna trascrizione disponibile');
  });

  it('should display the load failure passed by the parent without fake text', () => {
    fixture.componentRef.setInput('loadError', 'Caricamento della trascrizione fallito.');
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Caricamento della trascrizione fallito.',
    );
    expect(element.querySelector('textarea')).toBeNull();
    expect(fixture.componentInstance.draftText()).toBeNull();
  });

  it('should preserve the corrected draft across load errors and recovery', () => {
    setTranscript({ id: 'job-53', text: 'Testo originale.' });
    edit('Bozza modificata.');
    fixture.componentRef.setInput('loadError', 'Errore temporaneo.');
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Errore temporaneo.');
    expect(fixture.componentInstance.draftText()).toBe('Bozza modificata.');
    expect(textarea().value).toBe('Bozza modificata.');

    fixture.componentRef.setInput('loadError', null);
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(textarea().value).toBe('Bozza modificata.');
    expect(fixture.componentInstance.draftText()).toBe('Bozza modificata.');
  });

  it('should retain an intentionally cleared transcript despite subsequent source updates', () => {
    setTranscript({ id: 'job-53', text: 'Testo originale.' });
    edit('');

    setTranscript({ id: 'job-53', text: 'Aggiornamento da non sovrascrivere.' });

    expect(textarea().value).toBe('');
    expect(fixture.componentInstance.draftText()).toBe('');
    expect(fixture.componentInstance.isDirty()).toBe(true);
  });

  it('should allow editing a loaded transcript that is initially empty', () => {
    setTranscript({ id: 'job-53', text: '' });
    expect(textarea().value).toBe('');
    expect(fixture.componentInstance.isDirty()).toBe(false);

    edit('Testo inserito.');

    expect(fixture.componentInstance.draftText()).toBe('Testo inserito.');
    expect(fixture.componentInstance.isDirty()).toBe(true);
  });

  it('should accept source updates again after the user restores the original text', () => {
    setTranscript({ id: 'job-53', text: 'Testo originale.' });
    edit('Correzioni locali.');
    edit('Testo originale.');
    expect(fixture.componentInstance.isDirty()).toBe(false);

    setTranscript({ id: 'job-53', text: 'Nuovo testo dalla sorgente.' });

    expect(textarea().value).toBe('Nuovo testo dalla sorgente.');
    expect(fixture.componentInstance.isDirty()).toBe(false);
  });
});
