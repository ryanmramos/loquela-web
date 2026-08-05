import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Paginator } from 'primeng/paginator';
import { PaginatorState } from 'primeng/types/paginator';
import { TranslatePipe } from '@core/i18n/translate.pipe';

@Component({
  selector: 'app-pagination',
  imports: [Paginator, TranslatePipe],
  templateUrl: './pagination.html',
  styleUrl: './pagination.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pagination {
  readonly currentPage = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  changePage(event: PaginatorState): void {
    const page = (event.page ?? 0) + 1;
    if (page >= 1 && page <= this.totalPages() && page !== this.currentPage()) {
      this.pageChange.emit(page);
    }
  }
}
