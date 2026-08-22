import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';

import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { AppIcon } from '@app/shared/components/icon/app-icon';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';

@Component({
  selector: 'app-timed-transcript-help',
  imports: [AppIcon, ButtonDirective, TranslatePipe],
  templateUrl: './timed-transcript-help.html',
  styleUrl: './timed-transcript-help.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimedTranscriptHelp {
  private readonly translations = inject(TranslationService);

  readonly supportedLanguages = input<readonly string[]>([]);
  readonly copied = signal<string | null>(null);
  readonly copyFailed = signal(false);
  readonly videoUrl = signal('');
  readonly languageCode = signal('');
  readonly subtitleChoice = signal<'prefer-manual' | 'manual' | 'automatic'>('prefer-manual');
  readonly format = signal<'vtt' | 'srt'>('vtt');
  readonly shell = signal<'posix' | 'powershell'>('posix');
  readonly listCommand = 'yt-dlp --list-subs "VIDEO_URL"';
  readonly vttCommand =
    'yt-dlp --skip-download --write-subs --write-auto-subs --sub-langs "LANGUAGE_CODE" --sub-format "vtt/best" --convert-subs vtt --output "%(title)s.%(ext)s" "VIDEO_URL"';
  readonly srtCommand =
    'yt-dlp --skip-download --write-subs --write-auto-subs --sub-langs "LANGUAGE_CODE" --sub-format "vtt/best" --convert-subs srt --output "%(title)s.%(ext)s" "VIDEO_URL"';

  async copy(name: string, command: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(command);
      this.copyFailed.set(false);
      this.copied.set(name);
    } catch {
      this.copied.set(null);
      this.copyFailed.set(true);
    }
  }

  generatedCommand(): string {
    const video = this.quote(this.videoUrl().trim() || 'VIDEO_URL');
    const language = this.quote(this.languageCode().trim() || 'LANGUAGE_CODE');
    const subtitleFlags = {
      'prefer-manual': '--write-subs --write-auto-subs',
      manual: '--write-subs',
      automatic: '--write-auto-subs',
    }[this.subtitleChoice()];
    const format = this.format();
    return `yt-dlp --skip-download ${subtitleFlags} --sub-langs ${language} --sub-format ${this.quote('vtt/best')} --convert-subs ${format} --output ${this.quote('%(title)s.%(ext)s')} ${video}`;
  }

  setVideoUrl(value: string): void {
    this.videoUrl.set(value);
  }
  setLanguageCode(value: string): void {
    this.languageCode.set(value);
  }

  languageName(code: string): string {
    try {
      return (
        new Intl.DisplayNames([this.translations.language()], { type: 'language' }).of(code) ?? code
      );
    } catch {
      return code;
    }
  }

  languageOptions(): readonly string[] {
    return [...new Set(this.supportedLanguages())];
  }

  setSubtitleChoice(value: string): void {
    if (value === 'prefer-manual' || value === 'manual' || value === 'automatic') {
      this.subtitleChoice.set(value);
    }
  }
  setFormat(value: string): void {
    if (value === 'vtt' || value === 'srt') this.format.set(value);
  }
  setShell(value: string): void {
    if (value === 'posix' || value === 'powershell') this.shell.set(value);
  }

  private quote(value: string): string {
    // The generator only renders text. Each value is quoted for the selected
    // shell and is never executed or sent to Loquela.
    return this.shell() === 'powershell'
      ? `'${value.replaceAll("'", "''")}'`
      : `'${value.replaceAll("'", `'"'"'`)}'`;
  }
}
