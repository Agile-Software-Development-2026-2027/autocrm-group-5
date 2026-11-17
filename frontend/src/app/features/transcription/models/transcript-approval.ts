export interface TranscriptApprovalRequest {
  readonly dealCode: string;
  readonly text: string;
}

export interface TranscriptApprovalResponse {
  readonly status: 'success';
  readonly dealId?: string;
  readonly noteId?: string;
}

export type TranscriptDecision =
  | { readonly kind: 'approved'; readonly response: TranscriptApprovalResponse }
  | { readonly kind: 'discarded' };
