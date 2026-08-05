import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { ActionMenu, ActionMenuItem } from '@app/shared/components/action-menu/action-menu';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { ToastRegion } from '@app/shared/components/toast/toast-region';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { ThemeService } from '@core/theme/theme.service';
import { TranslationService } from '@core/i18n/translation.service';

interface ShellNavigationItem {
  readonly key:
    | 'navigation.overview'
    | 'navigation.sources'
    | 'navigation.lookup'
    | 'navigation.cards'
    | 'navigation.decks'
    | 'navigation.study'
    | 'navigation.settings';
  readonly symbol: string;
  readonly target: string;
}

@Component({
  selector: 'app-shell',
  imports: [ActionMenu, ButtonDirective, RouterLink, RouterLinkActive, ToastRegion, TranslatePipe],
  templateUrl: './app-shell.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {
  protected readonly themes = inject(ThemeService);
  private readonly translations = inject(TranslationService);
  readonly userName = input('Ryan Ramos');
  readonly userEmail = input('ryan@example.com');
  readonly activeJobCount = input<number | null>(null);
  readonly cardsToStudy = input<number | null>(null);
  readonly searchSubmitted = output<string>();
  readonly userActionSelected = output<string>();

  protected readonly navigation: readonly ShellNavigationItem[] = [
    { key: 'navigation.overview', symbol: '⌂', target: '/app' },
    { key: 'navigation.sources', symbol: '▤', target: '/app/sources' },
    { key: 'navigation.lookup', symbol: '⌕', target: '/app/lookup' },
    { key: 'navigation.cards', symbol: '▣', target: '/app/cards' },
    { key: 'navigation.decks', symbol: '▥', target: '/app/decks' },
    { key: 'navigation.study', symbol: '◉', target: '/app/study' },
    { key: 'navigation.settings', symbol: '⚙', target: '/app/settings' },
  ];

  protected readonly userActions: readonly ActionMenuItem[] = [
    { id: 'profile', label: '', labelKey: 'shell.profileSettings' },
    { id: 'sign-out', label: '', labelKey: 'shell.signOut', danger: true },
  ];

  submitSearch(event: SubmitEvent, searchInput: HTMLInputElement): void {
    event.preventDefault();
    const query = searchInput.value.trim();
    if (query.length > 0) {
      this.searchSubmitted.emit(query);
    }
  }

  focusMain(event: MouseEvent, main: HTMLElement): void {
    event.preventDefault();
    main.focus();
  }

  themeToggleLabel(): string {
    return this.themes.theme() === 'dark'
      ? this.translations.translate('shell.switchLight')
      : this.translations.translate('shell.switchDark');
  }

  initials(): string {
    return this.userName()
      .split(/\s+/u)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('');
  }
}
