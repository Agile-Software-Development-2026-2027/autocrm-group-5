import { ComponentFixture, TestBed } from '@angular/core/testing';
import { type Mock, vi } from 'vitest';
import { AudioFilePicker } from './audio-file-picker';

describe('AudioFilePicker', () => {
  let fixture: ComponentFixture<AudioFilePicker>;
  let element: HTMLElement;
  let input: HTMLInputElement;
  let onFileSelected: Mock<(file: File | null) => void>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AudioFilePicker],
    }).compileComponents();

    fixture = TestBed.createComponent(AudioFilePicker);
    fixture.detectChanges();
    element = fixture.nativeElement;
    input = element.querySelector<HTMLInputElement>('input[type="file"]')!;
    onFileSelected = vi.fn<(file: File | null) => void>();
    fixture.componentInstance.fileSelected.subscribe(onFileSelected);
  });

  function selectFile(file: File | null): void {
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: file ? [file] : [],
    });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
  }

  function audioFile(name = 'registrazione.mp3', type = 'audio/mpeg', size?: number): File {
    const file = new File(['audio'], name, { type });

    if (size !== undefined) {
      // Simula solo il metadato: il test non alloca file da 100 MB.
      Object.defineProperty(file, 'size', { value: size });
    }

    return file;
  }

  function errorText(): string | null {
    return element.querySelector('[role="alert"]')?.textContent ?? null;
  }

  function statusText(): string {
    return element.querySelector('[role="status"]')?.textContent ?? '';
  }

  it('should offer an accessible single-file input with format and size hints', () => {
    expect(input.multiple).toBe(false);
    expect(element.querySelector('label')?.htmlFor).toBe(input.id);
    expect(input.accept.split(',')).toEqual([
      'audio/*',
      'application/ogg',
      'application/mp4',
      'video/mp4',
      'video/webm',
    ]);
    expect(element.querySelector('#audio-file-formats')?.textContent).not.toContain(
      'Formati supportati:',
    );
    expect(element.querySelector('#audio-file-compatibility')?.textContent).toContain(
      'non garantisce',
    );
    expect(element.querySelector('#audio-file-limit')?.textContent).toContain('100 MB');
    expect(input.getAttribute('aria-invalid')).toBe('false');
    expect(statusText()).toContain('Nessun file selezionato');
    expect(errorText()).toBeNull();
    expect(onFileSelected).not.toHaveBeenCalled();
  });

  it('should display the valid filename and emit the original File', () => {
    const file = audioFile();

    selectFile(file);

    expect(statusText()).toContain(file.name);
    expect(errorText()).toBeNull();
    expect(onFileSelected).toHaveBeenCalledTimes(1);
    expect(onFileSelected).toHaveBeenCalledWith(file);
  });

  it('should block file changes while disabled and allow selection again afterwards', () => {
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(input.disabled).toBe(true);

    const file = audioFile();
    selectFile(file);
    expect(onFileSelected).not.toHaveBeenCalled();

    fixture.componentRef.setInput('disabled', false);
    fixture.detectChanges();
    selectFile(file);

    expect(input.disabled).toBe(false);
    expect(onFileSelected).toHaveBeenLastCalledWith(file);
  });

  it('should replace a valid selection with the new file', () => {
    const firstFile = audioFile('prima.mp3');
    const nextFile = audioFile('seconda.wav', 'audio/wav');

    selectFile(firstFile);
    selectFile(nextFile);

    expect(statusText()).toContain(nextFile.name);
    expect(statusText()).not.toContain(firstFile.name);
    expect(onFileSelected).toHaveBeenCalledTimes(2);
    expect(onFileSelected).toHaveBeenLastCalledWith(nextFile);
  });

  it('should reject a non-audio file with a clear message and emit null', () => {
    selectFile(new File(['testo'], 'documento.txt', { type: 'text/plain' }));

    expect(errorText()).toContain('non è riconosciuto come audio');
    expect(statusText()).toContain('Nessun file selezionato');
    expect(onFileSelected).toHaveBeenCalledTimes(1);
    expect(onFileSelected).toHaveBeenCalledWith(null);
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-errormessage')).toBe('audio-file-error');
  });

  it.each([
    ['registrazione.aiff', 'audio/aiff'],
    ['registrazione', 'audio/opus'],
    ['mp3', 'audio/mpeg'],
    ['registrazione.nuovo', 'audio/vnd.nuovo-formato'],
  ])('should accept audio MIME without an extension whitelist: %s, %s', (name, type) => {
    const file = audioFile(name, type);

    selectFile(file);

    expect(errorText()).toBeNull();
    expect(statusText()).toContain(file.name);
    expect(onFileSelected).toHaveBeenLastCalledWith(file);
  });

  it('should reject a file one byte above the backend limit', () => {
    selectFile(audioFile('grande.mp3', 'audio/mpeg', 104_857_601));

    expect(errorText()).toContain('supera il limite di 100 MB');
    expect(statusText()).toContain('Nessun file selezionato');
    expect(onFileSelected).toHaveBeenLastCalledWith(null);
  });

  it('should accept a file exactly at the backend limit of 100 * 1024 * 1024 bytes', () => {
    const file = audioFile('limite.mp3', 'audio/mpeg', 104_857_600);

    selectFile(file);

    expect(errorText()).toBeNull();
    expect(statusText()).toContain(file.name);
    expect(onFileSelected).toHaveBeenLastCalledWith(file);
  });

  it('should reject an empty audio file with a dedicated message', () => {
    selectFile(new File([], 'vuoto.mp3', { type: 'audio/mpeg' }));

    expect(errorText()).toContain('Il file è vuoto');
    expect(statusText()).toContain('Nessun file selezionato');
    expect(onFileSelected).toHaveBeenLastCalledWith(null);
  });

  it('should accept uppercase extensions', () => {
    const file = audioFile('REGISTRAZIONE.AIFF', 'audio/aiff');

    selectFile(file);

    expect(errorText()).toBeNull();
    expect(statusText()).toContain(file.name);
    expect(onFileSelected).toHaveBeenLastCalledWith(file);
  });

  it.each([
    ['registrazione.mp3', ''],
    ['registrazione.wav', ''],
    ['registrazione.aiff', ''],
    ['registrazione', ''],
    ['registrazione.mp3', 'application/octet-stream'],
    ['registrazione.flac', 'binary/octet-stream'],
    ['registrazione', 'application/octet-stream'],
    ['registrazione.mp4', 'application/octet-stream'],
  ])(
    'should reject missing or generic MIME without trusting the extension: %s, %s',
    (name, type) => {
      selectFile(audioFile(name, type));

      expect(errorText()).toContain('il browser non ha indicato un tipo di file specifico');
      expect(statusText()).toContain('Nessun file selezionato');
      expect(input.getAttribute('aria-invalid')).toBe('true');
      expect(onFileSelected).toHaveBeenCalledTimes(1);
      expect(onFileSelected).toHaveBeenCalledWith(null);
    },
  );

  it.each(['text/plain', 'image/png', 'application/pdf', 'video/quicktime', 'audio/'])(
    'should reject clearly incompatible MIME %s even with an mp3 extension',
    (type) => {
      selectFile(audioFile('rinominato.mp3', type));

      expect(errorText()).toContain('non è riconosciuto come audio');
      expect(onFileSelected).toHaveBeenLastCalledWith(null);
    },
  );

  it.each([
    ['registrazione', 'application/ogg'],
    ['registrazione.oga', 'application/ogg'],
    ['registrazione.opus', 'application/ogg'],
    ['registrazione.mp4', 'video/mp4'],
    ['registrazione.senza-vincoli', 'video/mp4'],
    ['registrazione.mp4', 'application/mp4'],
    ['registrazione.m4a', 'application/mp4'],
    ['registrazione', 'video/webm'],
  ])('should accept supported container %s with MIME %s', (name, type) => {
    const file = audioFile(name, type);

    selectFile(file);

    expect(errorText()).toBeNull();
    expect(onFileSelected).toHaveBeenLastCalledWith(file);
  });

  it('should reject an unrecognized application MIME without falling back to the extension', () => {
    selectFile(audioFile('registrazione.flac', 'application/x-flac'));

    expect(errorText()).toContain('non è riconosciuto come audio');
    expect(onFileSelected).toHaveBeenLastCalledWith(null);
  });

  it.each(['audio/webm; codecs=opus', 'video/webm; codecs=opus'])(
    'should recognize MIME %s with codec parameters',
    (type) => {
      const file = audioFile('registrazione', type);

      selectFile(file);

      expect(errorText()).toBeNull();
      expect(onFileSelected).toHaveBeenLastCalledWith(file);
    },
  );

  it('should clear the previous valid file and emit null after an invalid selection', () => {
    const validFile = audioFile();
    selectFile(validFile);

    selectFile(new File(['testo'], 'documento.txt', { type: 'text/plain' }));

    expect(statusText()).not.toContain(validFile.name);
    expect(statusText()).toContain('Nessun file selezionato');
    expect(errorText()).toContain('non è riconosciuto come audio');
    expect(onFileSelected).toHaveBeenCalledTimes(2);
    expect(onFileSelected).toHaveBeenNthCalledWith(1, validFile);
    expect(onFileSelected).toHaveBeenNthCalledWith(2, null);
  });

  it('should clear a valid file when the next selection has unknown MIME', () => {
    const validFile = audioFile();
    selectFile(validFile);

    selectFile(audioFile('senza-tipo.mp3', ''));

    expect(statusText()).not.toContain(validFile.name);
    expect(statusText()).toContain('Nessun file selezionato');
    expect(errorText()).toContain('il browser non ha indicato un tipo di file specifico');
    expect(onFileSelected).toHaveBeenCalledTimes(2);
    expect(onFileSelected).toHaveBeenLastCalledWith(null);
  });

  it('should clear the unknown MIME error after selecting recognized audio', () => {
    selectFile(audioFile('sconosciuto.mp3', 'application/octet-stream'));
    expect(errorText()).toContain('il browser non ha indicato un tipo di file specifico');

    const file = audioFile('nuova.aiff', 'audio/aiff');
    selectFile(file);

    expect(errorText()).toBeNull();
    expect(statusText()).toContain(file.name);
    expect(input.getAttribute('aria-invalid')).toBe('false');
    expect(onFileSelected).toHaveBeenLastCalledWith(file);
  });

  it('should clear the error and restore validity after a new valid selection', () => {
    selectFile(new File([], 'vuoto.mp3', { type: 'audio/mpeg' }));
    expect(errorText()).toContain('Il file è vuoto');

    const file = audioFile('nuova.mp3');
    selectFile(file);

    expect(errorText()).toBeNull();
    expect(input.getAttribute('aria-invalid')).toBe('false');
    expect(input.hasAttribute('aria-errormessage')).toBe(false);
    expect(statusText()).toContain(file.name);
    expect(onFileSelected).toHaveBeenLastCalledWith(file);
  });

  it('should reset the native input so the same file can be selected again', () => {
    const resetInput = vi.spyOn(input, 'value', 'set');
    const file = audioFile();

    selectFile(file);
    expect(resetInput).toHaveBeenLastCalledWith('');

    selectFile(file);

    expect(resetInput).toHaveBeenCalledTimes(2);
    expect(onFileSelected).toHaveBeenCalledTimes(2);
    expect(onFileSelected).toHaveBeenNthCalledWith(1, file);
    expect(onFileSelected).toHaveBeenNthCalledWith(2, file);
    expect(statusText()).toContain(file.name);
  });

  it('should ignore an empty selection without showing an error or emitting a value', () => {
    selectFile(null);

    expect(statusText()).toContain('Nessun file selezionato');
    expect(errorText()).toBeNull();
    expect(onFileSelected).not.toHaveBeenCalled();
  });

  it('should preserve the valid selection when the native dialog is cancelled', () => {
    const file = audioFile();
    selectFile(file);

    input.dispatchEvent(new Event('cancel', { bubbles: true }));
    fixture.detectChanges();

    expect(statusText()).toContain(file.name);
    expect(errorText()).toBeNull();
    expect(onFileSelected).toHaveBeenCalledTimes(1);
    expect(onFileSelected).toHaveBeenLastCalledWith(file);
  });

  it('should preserve the valid selection when cancellation produces an empty file list', () => {
    const file = audioFile();
    selectFile(file);

    selectFile(null);

    expect(statusText()).toContain(file.name);
    expect(errorText()).toBeNull();
    expect(onFileSelected).toHaveBeenCalledTimes(1);
    expect(onFileSelected).toHaveBeenLastCalledWith(file);
  });
});
