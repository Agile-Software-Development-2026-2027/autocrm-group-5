import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { App } from './app';
import { appConfig } from './app.config';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [...appConfig.providers, provideHttpClientTesting()],
    }).compileComponents();
  });

  async function renderApp() {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('should start with the AutoCRM shell and the upload page', async () => {
    const fixture = await renderApp();
    const element: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance).toBeTruthy();
    expect(element.querySelector('.brand')?.textContent).toContain('AutoCRM');
    expect(element.querySelector('nav')?.getAttribute('aria-label')).toBe('Navigazione principale');
    expect(element.querySelector('main h1')?.textContent).toBe('Audio Upload');
  });

  it('should navigate using both links and mark the current page', async () => {
    const fixture = await renderApp();
    const element: HTMLElement = fixture.nativeElement;
    const router = TestBed.inject(Router);
    const uploadLink = element.querySelector<HTMLAnchorElement>('nav a[href="/audio-upload"]')!;
    const reviewLink = element.querySelector<HTMLAnchorElement>(
      'nav a[href="/transcript-review"]',
    )!;

    expect(uploadLink.getAttribute('aria-current')).toBe('page');
    expect(reviewLink.hasAttribute('aria-current')).toBe(false);

    reviewLink.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/transcript-review');
    expect(element.querySelector('main h1')?.textContent).toBe('Transcript Review');
    expect(reviewLink.getAttribute('aria-current')).toBe('page');
    expect(uploadLink.hasAttribute('aria-current')).toBe(false);

    uploadLink.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/audio-upload');
    expect(element.querySelector('main h1')?.textContent).toBe('Audio Upload');
    expect(uploadLink.getAttribute('aria-current')).toBe('page');
    expect(reviewLink.hasAttribute('aria-current')).toBe(false);
  });
});

describe('Keyboard focus on navigation', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [...appConfig.providers, provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should move focus to the new page heading', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/audio-upload');
    await fixture.whenStable();
    fixture.detectChanges();

    await TestBed.inject(Router).navigateByUrl('/transcript-review');
    await fixture.whenStable();
    fixture.detectChanges();
    const heading = fixture.nativeElement.querySelector('main h1');
    expect(document.activeElement).toBe(heading);
    expect(heading?.textContent).toBe('Transcript Review');
  });

  it('should preserve input focus when only the fragment changes', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/audio-upload');
    await fixture.whenStable();
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;
    input.focus();

    await TestBed.inject(Router).navigateByUrl('/audio-upload#audio-file');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(document.activeElement).toBe(input);
  });
});
