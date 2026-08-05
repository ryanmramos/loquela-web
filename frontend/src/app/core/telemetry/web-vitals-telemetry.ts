import { inject, Injectable, InjectionToken } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

import { RUNTIME_CONFIG } from '@core/config/runtime-config';
import { TranslationService } from '@core/i18n/translation.service';

export type WebVitalName = 'CLS' | 'INP' | 'LCP';

export interface WebVitalEvent {
  readonly metric: WebVitalName;
  readonly value: number;
  readonly routePattern: string;
  readonly releaseId: string;
  readonly environment: 'local' | 'test' | 'staging' | 'production';
  readonly navigationType: string;
  readonly interfaceLanguage: string;
}

export interface TelemetrySink {
  record(event: WebVitalEvent): void;
}

export const TELEMETRY_SINK = new InjectionToken<TelemetrySink>('TELEMETRY_SINK', {
  providedIn: 'root',
  factory: () => ({ record: () => undefined }),
});

@Injectable({ providedIn: 'root' })
export class WebVitalsTelemetry {
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly router = inject(Router);
  private readonly translations = inject(TranslationService);
  private readonly sink = inject(TELEMETRY_SINK);
  private routePattern = '/';
  private started = false;
  private cumulativeLayoutShift = 0;

  start(): void {
    if (
      this.started ||
      !this.config.telemetryEnabled ||
      typeof PerformanceObserver === 'undefined'
    ) {
      return;
    }
    this.started = true;
    this.updateRoutePattern();
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => this.updateRoutePattern());
    this.observeLargestContentfulPaint();
    this.observeLayoutShift();
    this.observeInteractions();
  }

  /** Captures only the reviewed allowlist; no arbitrary context object can cross this boundary. */
  capture(metric: WebVitalName, value: number, routePattern = this.routePattern): void {
    if (!Number.isFinite(value) || value < 0 || !isRoutePattern(routePattern)) return;
    this.sink.record({
      metric,
      value: coarsen(metric, value),
      routePattern,
      releaseId: this.config.releaseId ?? 'unversioned',
      environment: this.config.environmentName,
      navigationType: navigationType(),
      interfaceLanguage: this.translations.language(),
    });
  }

  private updateRoutePattern(): void {
    const parts: string[] = [];
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild) {
      route = route.firstChild;
      const configuredPath = route.routeConfig?.path;
      if (configuredPath) parts.push(configuredPath);
    }
    this.routePattern = parts.length ? `/${parts.join('/')}` : '/';
  }

  private observeLargestContentfulPaint(): void {
    observe('largest-contentful-paint', (entries) => {
      const latest = entries.at(-1);
      if (latest) this.capture('LCP', latest.startTime);
    });
  }

  private observeLayoutShift(): void {
    observe('layout-shift', (entries) => {
      for (const entry of entries) {
        if (isLayoutShift(entry) && !entry.hadRecentInput) {
          this.cumulativeLayoutShift += entry.value;
        }
      }
      this.capture('CLS', this.cumulativeLayoutShift);
    });
  }

  private observeInteractions(): void {
    observe('event', (entries) => {
      const longest = entries.reduce((duration, entry) => Math.max(duration, entry.duration), 0);
      if (longest) this.capture('INP', longest);
    });
  }
}

function observe(type: string, callback: (entries: readonly PerformanceEntry[]) => void): void {
  try {
    const observer = new PerformanceObserver((list) => callback(list.getEntries()));
    observer.observe({ type, buffered: true });
  } catch {
    // Unsupported entry types are expected in Firefox/WebKit; collection stays optional.
  }
}

function isLayoutShift(entry: PerformanceEntry): entry is PerformanceEntry & {
  readonly value: number;
  readonly hadRecentInput: boolean;
} {
  const candidate = entry as PerformanceEntry & {
    readonly value?: unknown;
    readonly hadRecentInput?: unknown;
  };
  return typeof candidate.value === 'number' && typeof candidate.hadRecentInput === 'boolean';
}

function isRoutePattern(value: string): boolean {
  return (
    value.startsWith('/') && !/[?#]/u.test(value) && !/[0-9a-f]{8}-[0-9a-f-]{27,}/iu.test(value)
  );
}

function coarsen(metric: WebVitalName, value: number): number {
  return metric === 'CLS' ? Math.round(value * 1_000) / 1_000 : Math.round(value / 10) * 10;
}

function navigationType(): string {
  const navigation = performance.getEntriesByType('navigation')[0];
  return typeof PerformanceNavigationTiming !== 'undefined' &&
    navigation instanceof PerformanceNavigationTiming
    ? navigation.type
    : 'navigate';
}
