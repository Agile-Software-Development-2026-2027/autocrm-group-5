import { Injectable, signal } from '@angular/core';
import { TranscriptApprovalResponse, TranscriptDecision } from '../models/transcript-approval';
import { TranscriptSource } from '../models/transcript-source';

/** Passaggio in memoria del risultato reale dalla pagina Upload alla Review. */
@Injectable({ providedIn: 'root' })
export class TranscriptionSession {
  private readonly source = signal<TranscriptSource | null>(null);
  readonly transcript = this.source.asReadonly();
  private readonly decisions = signal<ReadonlyMap<string, TranscriptDecision>>(new Map());
  private readonly pendingApproval = signal<string | null>(null);
  readonly approvalInProgress = this.pendingApproval.asReadonly();

  setTranscript(transcript: TranscriptSource): void {
    if (this.decisionFor(transcript.id)) {
      return;
    }
    this.source.set(transcript);
  }

  clear(): void {
    this.source.set(null);
  }

  decisionFor(jobId: string | null): TranscriptDecision | null {
    return jobId ? (this.decisions().get(jobId) ?? null) : null;
  }

  canApprove(jobId: string): boolean {
    return this.source()?.id === jobId && !this.decisionFor(jobId) && !this.pendingApproval();
  }

  beginApproval(jobId: string): boolean {
    if (!this.canApprove(jobId)) {
      return false;
    }
    this.pendingApproval.set(jobId);
    return true;
  }

  completeApproval(
    jobId: string,
    response: TranscriptApprovalResponse,
    approvedText: string,
  ): void {
    if (this.pendingApproval() === jobId) {
      if (this.source()?.id === jobId) {
        this.source.set({ id: jobId, text: approvedText });
      }
      this.decisions.update((decisions) =>
        new Map(decisions).set(jobId, { kind: 'approved', response }),
      );
    }
  }

  endApproval(jobId: string): void {
    if (this.pendingApproval() === jobId) {
      this.pendingApproval.set(null);
    }
  }

  discard(jobId: string): boolean {
    if (
      this.source()?.id !== jobId ||
      this.pendingApproval() !== null ||
      this.decisionFor(jobId)?.kind === 'discarded'
    ) {
      return false;
    }
    this.decisions.update((decisions) => new Map(decisions).set(jobId, { kind: 'discarded' }));
    this.source.set(null);
    return true;
  }
}
