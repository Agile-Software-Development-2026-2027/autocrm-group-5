import { InjectionToken } from '@angular/core';

export const TRANSCRIPT_APPROVAL_CONFIG = new InjectionToken<{
  readonly endpoint: string;
  readonly timeoutMs: number;
}>('TRANSCRIPT_APPROVAL_CONFIG', {
  providedIn: 'root',
  // Endpoint operativo: osservazioni di integrazione in docs/backend-integration-observations.md.
  factory: () => ({ endpoint: '/hubspot/sync', timeoutMs: 60_000 }),
});
