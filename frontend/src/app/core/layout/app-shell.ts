import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Drawer } from 'primeng/drawer';
import { InputText } from 'primeng/inputtext';

import { ActionMenu, ActionMenuItem } from '@app/shared/components/action-menu/action-menu';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { ToastRegion } from '@app/shared/components/toast/toast-region';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { ThemeService } from '@core/theme/theme.service';
import { TranslationService } from '@core/i18n/translation.service';
import { AppIcon, AppIconName } from '@app/shared/components/icon/app-icon';

interface ShellNavigationItem {
  readonly key:
    | 'navigation.overview'
    | 'navigation.sources'
    | 'navigation.lookup'
    | 'navigation.cards'
    | 'navigation.decks'
    | 'navigation.study'
    | 'navigation.activity'
    | 'navigation.settings'
    | 'navigation.admin';
  readonly icon: AppIconName;
  readonly target: string;
  readonly adminOnly?: boolean;
}

interface ShellNavigationGroup {
  readonly label: 'navigation.learn' | 'navigation.library' | 'navigation.account';
  readonly items: readonly ShellNavigationItem[];
}

@Component({
  selector: 'app-shell',
  imports: [
    ActionMenu,
    AppIcon,
    ButtonDirective,
    Drawer,
    InputText,
    RouterLink,
    RouterLinkActive,
    ToastRegion,
    TranslatePipe,
  ],
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
  readonly isAdmin = input(false);
  readonly searchSubmitted = output<string>();
  readonly userActionSelected = output<string>();
  readonly mobileMenuOpen = signal(false);

  protected readonly navigationGroups: readonly ShellNavigationGroup[] = [
    {
      label: 'navigation.learn',
      items: [
        { key: 'navigation.overview', icon: 'home', target: '/app' },
        { key: 'navigation.study', icon: 'sparkles', target: '/app/study' },
        { key: 'navigation.lookup', icon: 'search', target: '/app/lookup' },
      ],
    },
    {
      label: 'navigation.library',
      items: [
        { key: 'navigation.sources', icon: 'document', target: '/app/sources' },
        { key: 'navigation.cards', icon: 'book', target: '/app/cards' },
        { key: 'navigation.decks', icon: 'library', target: '/app/decks' },
      ],
    },
    {
      label: 'navigation.account',
      items: [
        { key: 'navigation.activity', icon: 'activity', target: '/app/activity' },
        { key: 'navigation.settings', icon: 'settings', target: '/app/settings' },
        {
          key: 'navigation.admin',
          icon: 'shield',
          target: '/app/admin/access',
          adminOnly: true,
        },
      ],
    },
  ];

  protected readonly mobilePrimary: readonly ShellNavigationItem[] = [
    this.navigationGroups[0].items[0],
    this.navigationGroups[0].items[1],
    this.navigationGroups[1].items[0],
    this.navigationGroups[0].items[2],
  ];

  protected readonly mobileMore: readonly ShellNavigationItem[] = [
    this.navigationGroups[1].items[1],
    this.navigationGroups[1].items[2],
    ...this.navigationGroups[2].items,
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
