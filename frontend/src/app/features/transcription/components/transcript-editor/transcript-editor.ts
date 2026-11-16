import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-transcript-editor',
  standalone: true,
  templateUrl: './transcript-editor.html',
  styleUrl: './transcript-editor.css',
})
export class TranscriptEditor {
  readonly text = input<string | null>(null);
  readonly loadError = input<string | null>(null);
  readonly readOnly = input(false);
  readonly textChange = output<string>();

  protected onTextInput(value: string): void {
    if (!this.readOnly()) {
      this.textChange.emit(value);
    }
  }
}
