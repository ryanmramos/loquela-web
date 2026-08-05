import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonDirective } from '../button/button.directive';
import { TranslatePipe } from '@core/i18n/translate.pipe';

@Component({
  selector: 'app-pagination',
  imports: [ButtonDirective, TranslatePipe],
  templateUrl: './pagination.html',
  styleUrl: './pagination.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pagination {
  readonly currentPage = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  readonly canGoBack = computed(() => this.currentPage() > 1);
  readonly canGoForward = computed(() => this.currentPage() < this.totalPages());

  goTo(page: number): void {
    if (page >= 1 && page <= this.totalPages() && page !== this.currentPage()) {
      this.pageChange.emit(page);
    }
  }
}
