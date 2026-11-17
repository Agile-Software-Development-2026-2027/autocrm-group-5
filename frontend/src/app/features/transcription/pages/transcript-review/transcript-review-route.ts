import { Component, inject } from '@angular/core';
import { TranscriptionSession } from '../../data-access/transcription-session';
import { TranscriptReviewPage } from './transcript-review-page';

@Component({
  selector: 'app-transcript-review-route',
  standalone: true,
  imports: [TranscriptReviewPage],
  template: `<app-transcript-review-page
    [transcript]="session.transcript()"
    [actionsEnabled]="true"
  />`,
})
export class TranscriptReviewRoute {
  protected readonly session = inject(TranscriptionSession);
}
