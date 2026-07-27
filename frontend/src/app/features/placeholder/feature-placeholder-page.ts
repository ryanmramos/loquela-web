import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ButtonDirective } from '@app/shared/components/button/button.directive';

@Component({
  selector: 'app-feature-placeholder-page',
  imports: [ButtonDirective, RouterLink],
  template: `
    <section class="placeholder">
      <p class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ heading }}</h1>
      <p>{{ description }}</p>

      @if (filterDescription) {
        <div class="filter-summary" aria-live="polite">
          <strong>Current view</strong>
          <span>{{ filterDescription }}</span>
        </div>
      }

      @if (featureKey === 'settings') {
        <section class="account-card" aria-labelledby="whatsapp-title">
          <div>
            <p class="eyebrow">Linked accounts</p>
            <h2 id="whatsapp-title">WhatsApp linking</h2>
            <p>
              WhatsApp linking is coming soon. Your Google account already provides complete website
              access.
            </p>
          </div>
          <button appButton variant="secondary" type="button" disabled>
            Connect WhatsApp · Coming soon
          </button>
        </section>
      }

      <a appButton routerLink="/app">Return to overview</a>
    </section>
  `,
  styles: `
    .placeholder {
      width: min(100%, 56rem);
      margin: 0 auto;
      padding: var(--space-10);
    }

    .eyebrow {
      margin: 0 0 var(--space-2);
      color: var(--color-primary);
      font-size: var(--font-size-xs);
      font-weight: 800;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }

    h1,
    h2 {
      font-family: var(--font-family-display);
    }

    h1 {
      margin: 0;
      font-size: var(--font-size-2xl);
    }

    .placeholder > p:not(.eyebrow),
    .account-card p {
      color: var(--color-text-muted);
      line-height: var(--line-height-body);
    }

    .filter-summary,
    .account-card {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-5);
      margin: var(--space-6) 0;
      padding: var(--space-5);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      background: var(--color-surface-panel);
      box-shadow: var(--shadow-sm);
    }

    .filter-summary {
      justify-content: flex-start;
    }

    .filter-summary span {
      color: var(--color-text-muted);
    }

    .account-card h2,
    .account-card p:last-child {
      margin-bottom: 0;
    }

    @media (max-width: 42rem) {
      .placeholder {
        padding: var(--space-6) var(--space-4);
      }

      .account-card {
        align-items: flex-start;
        flex-direction: column;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FeaturePlaceholderPage {
  private readonly route = inject(ActivatedRoute);

  readonly featureKey = String(this.route.snapshot.data['featureKey'] ?? 'feature');
  readonly eyebrow = String(this.route.snapshot.data['eyebrow'] ?? 'Coming soon');
  readonly heading = String(this.route.snapshot.data['heading'] ?? 'This workspace is coming soon');
  readonly description = String(
    this.route.snapshot.data['description'] ??
      'The route is ready and will be completed in its implementation slice.',
  );
  readonly filterDescription = this.describeFilters();

  private describeFilters(): string | null {
    const filters = [...this.route.snapshot.queryParamMap.keys]
      .map((key) => `${key}: ${this.route.snapshot.queryParamMap.get(key)}`)
      .join(' · ');
    return filters || null;
  }
}
