import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranscriptEditor } from '../../components/transcript-editor/transcript-editor';
import { TranscriptReviewActions } from '../../components/transcript-review-actions/transcript-review-actions';
import { TranscriptionSession } from '../../data-access/transcription-session';
import { TranscriptSource } from '../../models/transcript-source';

interface ReviewDraft {
  readonly id: string;
  readonly originalText: string;
  readonly text: string;
}

@Component({
  selector: 'app-transcript-review-page',
  standalone: true,
  imports: [TranscriptEditor, TranscriptReviewActions, RouterLink],
  templateUrl: './transcript-review-page.html',
  styleUrl: './transcript-review-page.css',
})
export class TranscriptReviewPage {
  readonly transcript = input<TranscriptSource | null>(null);
  readonly loadError = input<string | null>(null);
  readonly actionsEnabled = input(false);
  private readonly session = inject(TranscriptionSession);
  private readonly discardedId = signal<string | null>(null);

  private readonly draft = linkedSignal<TranscriptSource | null, ReviewDraft | null>({
    source: this.transcript,
    computation: (source, previous) => {
      const current = previous?.value;
      const decision =
        this.actionsEnabled() && current ? this.session.decisionFor(current.id) : null;

      if (decision?.kind === 'discarded' && (!source || source.id === current?.id)) {
        return null;
      }

      // Dopo l'invio la sessione contiene lo snapshot effettivamente approvato,
      // anche se la pagina è stata riaperta durante la richiesta.
      if (decision?.kind === 'approved' && source && current && source.id === current.id) {
        return { ...current, text: source.text };
      }

      // Le nuove risposte della stessa sorgente non sovrascrivono le correzioni.
      // Anche una sorgente temporaneamente assente conserva la bozza modificata.
      if (
        current &&
        (current.text !== current.originalText ||
          decision?.kind === 'approved' ||
          (this.actionsEnabled() && this.session.approvalInProgress() === current.id)) &&
        (!source || source.id === current.id)
      ) {
        return current;
      }

      return source ? { id: source.id, originalText: source.text, text: source.text } : null;
    },
  });

  readonly draftText = computed(() => this.draft()?.text ?? null);
  readonly transcriptId = computed(() => this.draft()?.id ?? null);
  readonly isDirty = computed(() => {
    const draft = this.draft();
    return draft !== null && draft.text !== draft.originalText;
  });

  protected readonly isDiscarded = computed(
    () =>
      this.discardedId() !== null &&
      (!this.transcript() || this.transcript()?.id === this.discardedId()),
  );
  protected readonly editorLocked = computed(
    () =>
      this.actionsEnabled() &&
      (this.session.approvalInProgress() !== null ||
        this.session.decisionFor(this.transcriptId())?.kind === 'approved'),
  );

  protected onDiscarded(jobId: string): void {
    this.draft.set(null);
    this.discardedId.set(jobId);
  }

  protected onTextChange(text: string): void {
    if (this.editorLocked()) {
      return;
    }
    this.draft.update((draft) => (draft ? { ...draft, text } : null));
  }
}
