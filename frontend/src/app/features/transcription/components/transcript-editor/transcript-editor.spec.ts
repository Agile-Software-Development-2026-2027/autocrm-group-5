import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { type Mock, vi } from 'vitest';
import { TranscriptEditor } from './transcript-editor';

@Component({
  standalone: true,
  imports: [TranscriptEditor],
  template: `
    <app-transcript-editor
      [text]="text()"
      [loadError]="loadError()"
      [readOnly]="readOnly()"
      (textChange)="text.set($event)"
    />
  `,
})
class EditorTestHost {
  readonly text = signal<string | null>('Trascrizione iniziale.\nSeconda riga.');
  readonly loadError = signal<string | null>(null);
  readonly readOnly = signal(false);
}

describe('TranscriptEditor', () => {
  let fixture: ComponentFixture<EditorTestHost>;
  let element: HTMLElement;
  let onTextChange: Mock<(text: string) => void>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [EditorTestHost] }).compileComponents();
    fixture = TestBed.createComponent(EditorTestHost);
    fixture.detectChanges();
    element = fixture.nativeElement;
    onTextChange = vi.fn<(text: string) => void>();
    const editor: TranscriptEditor = fixture.debugElement.query(
      By.directive(TranscriptEditor),
    ).componentInstance;
    editor.textChange.subscribe(onTextChange);
  });

  function textarea(): HTMLTextAreaElement {
    return element.querySelector<HTMLTextAreaElement>('textarea')!;
  }

  function edit(text: string): void {
    textarea().value = text;
    textarea().dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
  }

  it('should display the transcript in an editable, labelled textarea', () => {
    expect(textarea().value).toBe('Trascrizione iniziale.\nSeconda riga.');
    expect(textarea().readOnly).toBe(false);
    expect(textarea().disabled).toBe(false);
    expect(element.querySelector('label')?.htmlFor).toBe(textarea().id);
    expect(onTextChange).not.toHaveBeenCalled();
  });

  it('should communicate the exact correction to the parent', () => {
    const correctedText = '  Testo corretto.\nCon una nuova riga.  ';
    edit(correctedText);

    expect(onTextChange).toHaveBeenCalledTimes(1);
    expect(onTextChange).toHaveBeenCalledWith(correctedText);
    expect(fixture.componentInstance.text()).toBe(correctedText);
  });

  it('should retain parent-owned corrections across subsequent renders', () => {
    edit('Testo corretto dall’utente.');
    fixture.detectChanges();
    fixture.detectChanges();

    expect(textarea().value).toBe('Testo corretto dall’utente.');
    expect(fixture.componentInstance.text()).toBe('Testo corretto dall’utente.');
    expect(onTextChange).toHaveBeenCalledTimes(1);
  });

  it('should preserve a completely cleared transcript as an editable empty string', () => {
    edit('');

    expect(fixture.componentInstance.text()).toBe('');
    expect(textarea().value).toBe('');
    expect(element.querySelector('.empty-state')).toBeNull();
    expect(onTextChange).toHaveBeenLastCalledWith('');
  });

  it('should display an empty state when no transcript is available', () => {
    fixture.componentInstance.text.set(null);
    fixture.detectChanges();

    expect(element.querySelector('textarea')).toBeNull();
    expect(element.querySelector('h2')?.textContent).toBe('Nessuna trascrizione disponibile');
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(onTextChange).not.toHaveBeenCalled();
  });

  it('should display a load failure without inventing transcript data', () => {
    fixture.componentInstance.text.set(null);
    fixture.componentInstance.loadError.set('Impossibile recuperare la trascrizione.');
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Impossibile recuperare la trascrizione.',
    );
    expect(element.querySelector('textarea')).toBeNull();
    expect(onTextChange).not.toHaveBeenCalled();
  });

  it('should keep existing corrections visible when a load error appears', () => {
    edit('Correzioni da conservare.');
    fixture.componentInstance.loadError.set('Caricamento fallito.');
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Caricamento fallito.');
    expect(textarea().value).toBe('Correzioni da conservare.');
    expect(textarea().getAttribute('aria-describedby')).toContain('transcript-load-error');
    expect(fixture.componentInstance.text()).toBe('Correzioni da conservare.');
    expect(onTextChange).toHaveBeenCalledTimes(1);
  });

  it('should remove a resolved error without changing the draft', () => {
    edit('Bozza conservata.');
    fixture.componentInstance.loadError.set('Errore temporaneo.');
    fixture.detectChanges();
    fixture.componentInstance.loadError.set(null);
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(textarea().value).toBe('Bozza conservata.');
    expect(textarea().getAttribute('aria-describedby')).toBe('transcript-help');
    expect(onTextChange).toHaveBeenCalledTimes(1);
  });

  it('should render deliberate parent draft changes without emitting duplicate user edits', () => {
    fixture.componentInstance.text.set('Nuova bozza esplicitamente fornita dal padre.');
    fixture.detectChanges();

    expect(textarea().value).toBe('Nuova bozza esplicitamente fornita dal padre.');
    expect(onTextChange).not.toHaveBeenCalled();
  });

  it('should refuse user edit events while the parent has locked the editor', () => {
    fixture.componentInstance.readOnly.set(true);
    fixture.detectChanges();
    expect(textarea().readOnly).toBe(true);
    edit('Modifica da ignorare.');
    expect(fixture.componentInstance.text()).toBe('Trascrizione iniziale.\nSeconda riga.');
    expect(onTextChange).not.toHaveBeenCalled();
  });
});
