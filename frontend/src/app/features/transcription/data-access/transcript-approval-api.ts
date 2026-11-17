import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, TimeoutError, defer, finalize, map, shareReplay, tap, timeout } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import {
  TranscriptApprovalRequest,
  TranscriptApprovalResponse,
} from '../models/transcript-approval';
import { TRANSCRIPT_APPROVAL_CONFIG } from './transcript-approval.config';
import { TranscriptionSession } from './transcription-session';

export class TranscriptApprovalError extends Error {}

@Injectable({ providedIn: 'root' })
export class TranscriptApprovalApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL).replace(/\/+$/, '');
  private readonly config = inject(TRANSCRIPT_APPROVAL_CONFIG);
  private readonly session = inject(TranscriptionSession);

  approve(
    jobId: string,
    payload: TranscriptApprovalRequest,
  ): Observable<TranscriptApprovalResponse> {
    const request: TranscriptApprovalRequest = { dealCode: payload.dealCode, text: payload.text };

    return defer(() => {
      if (!request.dealCode.trim()) {
        throw new TranscriptApprovalError('Inserisci il Deal Code prima di approvare.');
      }
      if (!request.text.trim()) {
        throw new TranscriptApprovalError(
          'La trascrizione è vuota. Inserisci il testo prima di approvare.',
        );
      }
      if (!this.session.beginApproval(jobId)) {
        throw new TranscriptApprovalError(
          'Questa trascrizione non è disponibile per una nuova approvazione.',
        );
      }

      return this.http.post<unknown>(`${this.baseUrl}${this.config.endpoint}`, request).pipe(
        timeout(this.config.timeoutMs),
        map((response): TranscriptApprovalResponse => {
          if (
            typeof response !== 'object' ||
            response === null ||
            !('status' in response) ||
            response.status !== 'success'
          ) {
            throw new TranscriptApprovalError(
              "Il backend ha restituito una risposta incompleta. L'esito non è verificabile: controlla che la nota non sia già stata creata prima di riprovare.",
            );
          }
          const body = response as Record<string, unknown>;
          return {
            status: 'success',
            dealId: optionalId(body['dealId']),
            noteId: optionalId(body['noteId']),
          };
        }),
        tap((response) => this.session.completeApproval(jobId, response, request.text)),
        finalize(() => this.session.endApproval(jobId)),
      );
    }).pipe(
      // La richiesta già avviata continua se la UI viene distrutta: l'esito resta nella sessione.
      // Non usare retry: una seconda richiesta potrebbe creare una nota duplicata.
      shareReplay({ bufferSize: 1, refCount: false }),
    );
  }
}

function optionalId(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined;
}

export function transcriptApprovalErrorMessage(error: unknown): string {
  if (error instanceof TranscriptApprovalError) {
    return error.message;
  }
  if (error instanceof TimeoutError) {
    return "Tempo di attesa superato. L'esito non è confermato: verifica la nota prima di riprovare.";
  }
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return "Errore di rete durante l'approvazione. L'esito non è confermato: verifica la nota prima di riprovare.";
    }
    const detail = typeof error.error?.error === 'string' ? error.error.error.trim() : '';
    if (error.status === 400) {
      return detail
        ? `Approvazione rifiutata: ${detail}`
        : 'Il backend ha rifiutato l’approvazione. Controlla Deal Code e testo.';
    }
    return detail
      ? `Errore del backend: ${detail} Verifica l'esito prima di riprovare.`
      : "Il backend non ha completato l'approvazione. Verifica l'esito prima di riprovare.";
  }
  return "Impossibile confermare l'approvazione. Verifica l'esito prima di riprovare.";
}
