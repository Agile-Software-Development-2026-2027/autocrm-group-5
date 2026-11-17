import { Component, input, output, signal } from '@angular/core';
import { AUDIO_FILE_PICKER_CONFIG } from './audio-file-picker.config';

@Component({
  selector: 'app-audio-file-picker',
  standalone: true,
  templateUrl: './audio-file-picker.html',
  styleUrl: './audio-file-picker.css',
})
export class AudioFilePicker {
  readonly disabled = input(false);
  readonly fileSelected = output<File | null>();

  protected readonly selectedFile = signal<File | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly config = AUDIO_FILE_PICKER_CONFIG;
  protected readonly acceptedFileTypes = ['audio/*', ...this.config.containerMimeTypes].join(',');

  protected onFileChange(input: HTMLInputElement): void {
    if (this.disabled()) {
      return;
    }
    const file = input.files?.[0];

    // Il File resta nello stato del componente. Il reset permette di riselezionarlo.
    input.value = '';

    // L'annullamento non cambia l'ultima selezione né emette un nuovo valore.
    if (!file) {
      return;
    }

    const error = this.validateFile(file);
    const selectedFile = error ? null : file;

    this.errorMessage.set(error);
    this.selectedFile.set(selectedFile);
    this.fileSelected.emit(selectedFile);
  }

  private validateFile(file: File): string | null {
    const mimeType = file.type.split(';', 1)[0].trim().toLowerCase();

    if (!mimeType || this.config.genericMimeTypes.some((value) => value === mimeType)) {
      return 'Non è possibile riconoscere questo file come audio: il browser non ha indicato un tipo di file specifico. Prova un altro file audio.';
    }

    if (!this.isSupportedMimeType(mimeType)) {
      return 'Il tipo di file non è riconosciuto come audio. Seleziona un file audio.';
    }

    if (file.size === 0) {
      return 'Il file è vuoto. Seleziona una registrazione audio non vuota.';
    }

    if (file.size > this.config.maxSizeBytes) {
      return `Il file supera il limite di ${this.config.maxSizeMb} MB. Seleziona un file più piccolo.`;
    }

    return null;
  }

  private isSupportedMimeType(mimeType: string): boolean {
    return (
      (mimeType.startsWith('audio/') && mimeType.length > 'audio/'.length) ||
      this.config.containerMimeTypes.some((value) => value === mimeType)
    );
  }
}
