import {
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  linkedSignal,
  output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  TranscriptApprovalApi,
  transcriptApprovalErrorMessage,
} from '../../data-access/transcript-approval-api';
import { TranscriptionSession } from '../../data-access/transcription-session';

@Component({
  selector: 'app-transcript-review-actions',
  standalone: true,
  templateUrl: './transcript-review-actions.html',
  styleUrl: './transcript-review-actions.css',
})
export class TranscriptReviewActions {
  readonly jobId = input.required<string>();
  readonly text = input.required<string>();
  readonly discarded = output<string>();

  private readonly api = inject(TranscriptApprovalApi);
  private readonly session = inject(TranscriptionSession);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly dealCode = linkedSignal<string, string>({
    source: this.jobId,
    computation: () => '',
  });
  protected readonly attempted = linkedSignal<string, boolean>({
    source: this.jobId,
    computation: () => false,
  });
  protected readonly requestError = linkedSignal<string, string | null>({
    source: this.jobId,
    computation: () => null,
  });
  protected readonly busy = computed(() => this.session.approvalInProgress() !== null);
  protected readonly eligible = computed(
    () => this.session.transcript()?.id === this.jobId() && !this.session.decisionFor(this.jobId()),
  );
  protected readonly approval = computed(() => {
    const decision = this.session.decisionFor(this.jobId());
    return decision?.kind === 'approved' ? decision.response : null;
  });
  protected readonly canDiscard = computed(
    () =>
      this.session.transcript()?.id === this.jobId() &&
      this.session.decisionFor(this.jobId())?.kind !== 'discarded',
  );
  protected readonly dealCodeError = computed(() =>
    this.attempted() && !this.dealCode().trim()
      ? 'Inserisci il Deal Code prima di approvare.'
      : null,
  );
  protected readonly textError = computed(() =>
    this.attempted() && !this.text().trim()
      ? 'La trascrizione è vuota. Inserisci il testo prima di approvare.'
      : null,
  );

  protected onDealCodeInput(value: string): void {
    this.dealCode.set(value);
    this.requestError.set(null);
  }

  protected approve(event: Event): void {
    event.preventDefault();
    if (this.busy() || !this.eligible()) {
      return;
    }
    this.attempted.set(true);
    this.requestError.set(null);
    if (this.dealCodeError() || this.textError()) {
      return;
    }

    this.api
      .approve(this.jobId(), { dealCode: this.dealCode(), text: this.text() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (error: unknown) => this.requestError.set(transcriptApprovalErrorMessage(error)),
      });
  }

  protected discard(): void {
    if (this.session.discard(this.jobId())) {
      this.discarded.emit(this.jobId());
    }
  }
}
