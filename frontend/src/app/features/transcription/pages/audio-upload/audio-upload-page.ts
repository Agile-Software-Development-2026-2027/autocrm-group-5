import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { finalize, switchMap, tap } from 'rxjs';
import { AudioFilePicker } from '../../components/audio-file-picker/audio-file-picker';
import { TranscriptionApi } from '../../data-access/transcription-api';
import { transcriptionErrorMessage } from '../../data-access/transcription-errors';
import { TranscriptionSession } from '../../data-access/transcription-session';

@Component({
  selector: 'app-audio-upload-page',
  standalone: true,
  imports: [AudioFilePicker],
  templateUrl: './audio-upload-page.html',
  styleUrl: './audio-upload-page.css',
})
export class AudioUploadPage {
  private readonly api = inject(TranscriptionApi);
  private readonly session = inject(TranscriptionSession);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly selectedFile = signal<File | null>(null);
  protected readonly phase = signal<'idle' | 'uploading' | 'transcribing'>('idle');
  protected readonly busy = computed(() => this.phase() !== 'idle');
  protected readonly errorMessage = signal<string | null>(null);

  protected onFileSelected(file: File | null): void {
    if (!this.busy()) {
      this.selectedFile.set(file);
      this.errorMessage.set(null);
    }
  }

  protected startTranscription(): void {
    const file = this.selectedFile();
    if (!file || this.busy()) {
      return;
    }

    this.session.clear();
    this.errorMessage.set(null);
    this.phase.set('uploading');

    this.api
      .upload(file)
      .pipe(
        tap(() => this.phase.set('transcribing')),
        switchMap(({ jobId }) => this.api.waitForTranscript(jobId)),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.phase.set('idle')),
      )
      .subscribe({
        next: async (transcript) => {
          this.session.setTranscript(transcript);
          try {
            if (!(await this.router.navigate(['/transcript-review']))) {
              this.errorMessage.set('Impossibile aprire la pagina di revisione. Riprova.');
            }
          } catch {
            this.errorMessage.set('Impossibile aprire la pagina di revisione. Riprova.');
          }
        },
        error: (error: unknown) => this.errorMessage.set(transcriptionErrorMessage(error)),
      });
  }
}
