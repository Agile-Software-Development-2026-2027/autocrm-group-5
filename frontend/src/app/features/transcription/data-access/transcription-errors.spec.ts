import { HttpErrorResponse } from '@angular/common/http';
import { TimeoutError } from 'rxjs';
import { TranscriptionError, transcriptionErrorMessage } from './transcription-errors';

describe('Readable transcription errors', () => {
  it.each([
    [0, 'Impossibile raggiungere il backend'],
    [400, 'formato non supportato'],
    [413, 'limite di dimensione'],
    [404, 'non è stata trovata'],
    [500, 'Riprova più tardi'],
  ])('should explain HTTP status %s', (status, expectedMessage) => {
    expect(
      transcriptionErrorMessage(new HttpErrorResponse({ status: status as number })),
    ).toContain(expectedMessage as string);
  });

  it('should show a readable timeout rather than an internal exception', () => {
    expect(transcriptionErrorMessage(new TimeoutError())).toContain('tempo di attesa');
  });

  it('should preserve the known job failure message', () => {
    expect(transcriptionErrorMessage(new TranscriptionError('Trascrizione non riuscita.'))).toBe(
      'Trascrizione non riuscita.',
    );
  });

  it('should use a generic message for unknown implementation failures', () => {
    expect(transcriptionErrorMessage(new Error('internal details'))).toBe(
      'Si è verificato un errore durante la trascrizione. Riprova.',
    );
  });
});
