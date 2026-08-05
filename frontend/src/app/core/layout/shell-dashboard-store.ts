import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ShellDashboardStore {
  private readonly activeActivityState = signal<number | null>(null);
  private readonly cardsToStudyState = signal<number | null>(null);

  readonly activeActivity = this.activeActivityState.asReadonly();
  readonly cardsToStudy = this.cardsToStudyState.asReadonly();

  update(activeActivity: number | null, cardsToStudy: number | null): void {
    this.activeActivityState.set(activeActivity);
    this.cardsToStudyState.set(cardsToStudy);
  }

  updateActivity(activeActivity: number | null): void {
    this.activeActivityState.set(activeActivity);
  }

  reset(): void {
    this.update(null, null);
  }
}
