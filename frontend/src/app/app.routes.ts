import { isDevMode } from '@angular/core';
import { Routes } from '@angular/router';

export function createAppRoutes(includeReviewDemo: boolean): Routes {
  const demoRoutes: Routes = includeReviewDemo
    ? [
        {
          path: 'transcript-review/demo',
          title: 'Demo Transcript Review | AutoCRM',
          loadComponent: () =>
            import('./features/transcription/demo/transcript-review-demo').then(
              (module) => module.TranscriptReviewDemo,
            ),
        },
      ]
    : [];

  return [
    { path: '', redirectTo: 'audio-upload', pathMatch: 'full' },
    {
      path: 'audio-upload',
      title: 'Audio Upload | AutoCRM',
      loadComponent: () =>
        import('./features/transcription/pages/audio-upload/audio-upload-page').then(
          (module) => module.AudioUploadPage,
        ),
    },
    ...demoRoutes,
    {
      path: 'transcript-review',
      title: 'Transcript Review | AutoCRM',
      loadComponent: () =>
        import('./features/transcription/pages/transcript-review/transcript-review-route').then(
          (module) => module.TranscriptReviewRoute,
        ),
    },
    { path: '**', redirectTo: 'audio-upload' },
  ];
}

export const routes: Routes = createAppRoutes(isDevMode());
