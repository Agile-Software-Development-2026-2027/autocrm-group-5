import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, filter, map, switchMap, take, timeout, timer } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url';
import { TranscriptSource } from '../models/transcript-source';
import { TranscriptionJob, TranscriptionUpload } from '../models/transcription-job';
import { TranscriptionError } from './transcription-errors';
import { TRANSCRIPTION_TIMING } from './transcription-timing';

@Injectable({ providedIn: 'root' })
export class TranscriptionApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL).replace(/\/+$/, '');
  private readonly timing = inject(TRANSCRIPTION_TIMING);

  upload(file: File): Observable<TranscriptionUpload> {
    const body = new FormData();
    body.append('file', file, file.name);

    // Il browser imposta Content-Type e boundary del multipart.
    return this.http.post<unknown>(`${this.baseUrl}/transcriptions/upload`, body).pipe(
      timeout(this.timing.uploadTimeoutMs),
      map((response): TranscriptionUpload => {
        if (
          !isRecord(response) ||
          typeof response['jobId'] !== 'string' ||
          !response['jobId'].trim() ||
          response['status'] !== 'processing'
        ) {
          throw invalidResponse();
        }
        return { jobId: response['jobId'], status: 'processing' };
      }),
    );
  }

  getStatus(jobId: string): Observable<TranscriptionJob> {
    return this.http
      .get<unknown>(`${this.baseUrl}/transcriptions/${encodeURIComponent(jobId)}`)
      .pipe(
        timeout(this.timing.requestTimeoutMs),
        map((response): TranscriptionJob => {
          if (!isRecord(response) || response['jobId'] !== jobId) {
            throw invalidResponse();
          }

          const status = response['status'];
          if (status === 'processing' || status === 'failed') {
            return { jobId, status, transcript: null };
          }
          if (status === 'done' && typeof response['transcript'] === 'string') {
            return { jobId, status, transcript: response['transcript'] };
          }
          throw invalidResponse();
        }),
      );
  }

  waitForTranscript(jobId: string): Observable<TranscriptSource> {
    return this.getStatus(jobId).pipe(
      // Una nuova richiesta parte solo dopo la risposta precedente e l'intervallo.
      expand((job) =>
        job.status === 'processing'
          ? timer(this.timing.pollIntervalMs).pipe(switchMap(() => this.getStatus(jobId)))
          : EMPTY,
      ),
      filter((job) => job.status !== 'processing'),
      take(1),
      timeout({ first: this.timing.maxPollingMs }),
      map((job) => {
        if (job.status !== 'done') {
          throw new TranscriptionError(
            'La trascrizione non è riuscita. Puoi riprovare con un altro file.',
          );
        }
        return { id: job.jobId, text: job.transcript };
      }),
    );
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function invalidResponse(): TranscriptionError {
  return new TranscriptionError(
    'Il backend ha restituito una risposta di trascrizione non valida.',
  );
}
