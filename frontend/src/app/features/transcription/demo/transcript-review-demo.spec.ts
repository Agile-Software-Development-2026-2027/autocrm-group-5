import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranscriptReviewDemo } from './transcript-review-demo';

describe('TranscriptReviewDemo', () => {
  let fixture: ComponentFixture<TranscriptReviewDemo>;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TranscriptReviewDemo] }).compileComponents();
    fixture = TestBed.createComponent(TranscriptReviewDemo);
    fixture.detectChanges();
    element = fixture.nativeElement;
  });

  function clickButton(label: string): void {
    const button = Array.from(element.querySelectorAll('button')).find((button) =>
      button.textContent?.includes(label),
    )!;
    button.click();
    fixture.detectChanges();
  }

  it('should clearly label its fake data and allow a source update before editing', () => {
    expect(element.querySelector('aside')?.textContent).toContain('Modalità dimostrativa locale');
    expect(element.querySelector('aside')?.textContent).toContain('Dati fittizi');
    expect(element.querySelector('textarea')?.value).toContain('Testo dimostrativo locale.');

    clickButton('Simula aggiornamento');

    expect(element.querySelector('textarea')?.value).toContain(
      'Aggiornamento dimostrativo ricevuto dalla sorgente.',
    );
  });

  it('should demonstrate that parent updates and errors do not erase user corrections', () => {
    const textarea = element.querySelector('textarea')!;
    textarea.value = 'Correzione manuale della demo.';
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();

    clickButton('Simula aggiornamento');
    expect(textarea.value).toBe('Correzione manuale della demo.');

    clickButton('Simula errore');
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Errore dimostrativo');
    expect(textarea.value).toBe('Correzione manuale della demo.');

    clickButton('Rimuovi errore');
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(textarea.value).toBe('Correzione manuale della demo.');
  });
});
