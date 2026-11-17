import { Component, signal } from '@angular/core';
import { TranscriptSource } from '../models/transcript-source';
import { TranscriptReviewPage } from '../pages/transcript-review/transcript-review-page';

@Component({
  selector: 'app-transcript-review-demo',
  standalone: true,
  imports: [TranscriptReviewPage],
  templateUrl: './transcript-review-demo.html',
  styleUrl: './transcript-review-demo.css',
})
export class TranscriptReviewDemo {
  protected readonly source = signal<TranscriptSource>({
    id: 'demo-transcript-scrum-53',
    text: 'Testo dimostrativo locale.\nIl cliente desidera ricevere una proposta aggiornata entro venerdì.',
  });
  protected readonly loadError = signal<string | null>(null);

  protected simulateSourceUpdate(): void {
    this.source.update((source) => ({
      ...source,
      text: `${source.text}\nAggiornamento dimostrativo ricevuto dalla sorgente.`,
    }));
  }

  protected simulateLoadError(): void {
    this.loadError.set('Errore dimostrativo: impossibile caricare la trascrizione.');
  }
}
