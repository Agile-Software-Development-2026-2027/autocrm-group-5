import { InjectionToken } from '@angular/core';

export interface TranscriptionTiming {
  readonly pollIntervalMs: number;
  readonly maxPollingMs: number;
  readonly requestTimeoutMs: number;
  readonly uploadTimeoutMs: number;
}

export const TRANSCRIPTION_TIMING = new InjectionToken<TranscriptionTiming>(
  'TRANSCRIPTION_TIMING',
  {
    providedIn: 'root',
    factory: () => ({
      pollIntervalMs: 2000,
      maxPollingMs: 11 * 60 * 1000,
      requestTimeoutMs: 30_000,
      uploadTimeoutMs: 120_000,
    }),
  },
);
