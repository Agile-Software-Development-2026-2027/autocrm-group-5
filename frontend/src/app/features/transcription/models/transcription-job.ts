export interface TranscriptionUpload {
  readonly jobId: string;
  readonly status: 'processing';
}

export type TranscriptionJob =
  | { readonly jobId: string; readonly status: 'processing' | 'failed'; readonly transcript: null }
  | { readonly jobId: string; readonly status: 'done'; readonly transcript: string };
