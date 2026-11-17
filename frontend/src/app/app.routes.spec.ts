import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { createAppRoutes, routes } from './app.routes';
import { API_BASE_URL } from './core/config/api-base-url';

beforeEach(() => {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: '/api' },
    ],
  });
});

describe('Application routing', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes)],
    });
  });

  it('should open the upload page directly', async () => {
    const harness = await RouterTestingHarness.create('/audio-upload');

    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Audio Upload');
    expect(TestBed.inject(Router).url).toBe('/audio-upload');
  });

  it('should open the review page directly without transcript data', async () => {
    const harness = await RouterTestingHarness.create('/transcript-review');

    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Transcript Review');
    expect(TestBed.inject(Router).url).toBe('/transcript-review');
  });

  it('should redirect the initial URL to the upload page', async () => {
    const harness = await RouterTestingHarness.create('/');

    expect(TestBed.inject(Router).url).toBe('/audio-upload');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Audio Upload');
  });

  it('should redirect an unknown URL to the upload page', async () => {
    const harness = await RouterTestingHarness.create('/pagina-inesistente/dettaglio');

    expect(TestBed.inject(Router).url).toBe('/audio-upload');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Audio Upload');
  });
});

describe('Review demo routing', () => {
  it('should open the separate demo only when enabled', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(createAppRoutes(true))] });
    const harness = await RouterTestingHarness.create('/transcript-review/demo');

    expect(harness.routeNativeElement?.querySelector('aside')?.textContent).toContain(
      'Modalità dimostrativa locale',
    );
    expect(harness.routeNativeElement?.querySelector('textarea')?.value).toContain(
      'Testo dimostrativo locale.',
    );
  });

  it('should redirect the demo URL when demos are disabled as in production', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(createAppRoutes(false))] });
    const harness = await RouterTestingHarness.create('/transcript-review/demo');

    expect(TestBed.inject(Router).url).toBe('/audio-upload');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Audio Upload');
    expect(harness.routeNativeElement?.querySelector('textarea')).toBeNull();
  });
});
