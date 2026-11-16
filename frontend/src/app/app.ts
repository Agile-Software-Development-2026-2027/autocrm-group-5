import { Component, ElementRef, Injector, afterNextRender, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly mainContent = viewChild.required<ElementRef<HTMLElement>>('mainContent');

  constructor() {
    const router = inject(Router);
    const injector = inject(Injector);
    let lastPage = '';
    router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => {
        const page = event.urlAfterRedirects.split(/[?#]/, 1)[0];
        if (page === lastPage) return;
        lastPage = page;
        afterNextRender(
          () => {
            const heading = this.mainContent().nativeElement.querySelector('h1');
            if (heading) {
              heading.tabIndex = -1;
              heading.focus({ preventScroll: true });
            }
          },
          { injector },
        );
      });
  }
}
