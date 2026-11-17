import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../../../core/config/api-base-url';
import { AudioUploadPage } from './audio-upload-page';

describe('AudioUploadPage', () => {
  it('should render the upload page with a working audio file picker', async () => {
    await TestBed.configureTestingModule({
      imports: [AudioUploadPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(AudioUploadPage);
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('h1')?.textContent).toBe('Audio Upload');
    expect(element.querySelector('app-audio-file-picker')).not.toBeNull();
    expect(element.querySelector('[role="status"]')?.textContent).toContain(
      'Nessun file selezionato',
    );

    const input = element.querySelector<HTMLInputElement>('input[type="file"]')!;
    const file = new File(['audio'], 'registrazione.aiff', { type: 'audio/aiff' });
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await fixture.whenStable();

    expect(element.querySelector('[role="status"]')?.textContent).toContain(file.name);
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });
});
