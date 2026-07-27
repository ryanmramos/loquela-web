import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ShellDashboardStore {
  private readonly activeJobsState = signal<number | null>(null);
  private readonly cardsToStudyState = signal<number | null>(null);

  readonly activeJobs = this.activeJobsState.asReadonly();
  readonly cardsToStudy = this.cardsToStudyState.asReadonly();

  update(activeJobs: number | null, cardsToStudy: number | null): void {
    this.activeJobsState.set(activeJobs);
    this.cardsToStudyState.set(cardsToStudy);
  }

  reset(): void {
    this.update(null, null);
  }
}
