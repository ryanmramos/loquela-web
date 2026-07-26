import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { ActionMenu, ActionMenuItem } from '@app/shared/components/action-menu/action-menu';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { ToastRegion } from '@app/shared/components/toast/toast-region';

interface ShellNavigationItem {
  readonly label: string;
  readonly symbol: string;
  readonly target: string;
}

@Component({
  selector: 'app-shell',
  imports: [ActionMenu, ButtonDirective, ToastRegion],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {
  readonly userName = input('Ryan Ramos');
  readonly userEmail = input('ryan@example.com');
  readonly searchSubmitted = output<string>();
  readonly userActionSelected = output<string>();

  protected readonly navigation: readonly ShellNavigationItem[] = [
    { label: 'Overview', symbol: '⌂', target: '#overview' },
    { label: 'Sources', symbol: '▤', target: '#components' },
    { label: 'Cards', symbol: '▣', target: '#data' },
    { label: 'Decks', symbol: '▥', target: '#states' },
    { label: 'Settings', symbol: '⚙', target: '#settings' },
  ];

  protected readonly userActions: readonly ActionMenuItem[] = [
    { id: 'profile', label: 'Profile settings' },
    { id: 'sign-out', label: 'Sign out', danger: true },
  ];

  submitSearch(event: SubmitEvent, searchInput: HTMLInputElement): void {
    event.preventDefault();
    const query = searchInput.value.trim();
    if (query.length > 0) {
      this.searchSubmitted.emit(query);
    }
  }

  initials(): string {
    return this.userName()
      .split(/\s+/u)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('');
  }
}
