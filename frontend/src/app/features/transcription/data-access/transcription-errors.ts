import { HttpErrorResponse } from '@angular/common/http';
import { TimeoutError } from 'rxjs';

export class TranscriptionError extends Error {}

export function transcriptionErrorMessage(error: unknown): string {
  if (error instanceof TranscriptionError) {
    return error.message;
  }

  if (error instanceof TimeoutError) {
    return "L'operazione ha superato il tempo di attesa. Riprova.";
  }

  if (error instanceof HttpErrorResponse) {
    switch (error.status) {
      case 0:
        return 'Impossibile raggiungere il backend. Verifica che Spring Boot sia attivo e raggiungibile.';
      case 400:
        return 'Il backend ha rifiutato il file: formato non supportato o dimensione non consentita.';
      case 413:
        return 'Il file supera il limite di dimensione consentito dal backend.';
      case 404:
        return 'La trascrizione richiesta non è stata trovata sul backend.';
      default:
        return 'Il backend non riesce a completare la richiesta. Riprova più tardi.';
    }
  }

  return 'Si è verificato un errore durante la trascrizione. Riprova.';
}
