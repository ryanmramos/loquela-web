import { Pipe, PipeTransform, inject } from '@angular/core';

import { TranslationService } from './translation.service';

@Pipe({ name: 'translate', pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly translations = inject(TranslationService);

  transform(
    key: Parameters<TranslationService['translate']>[0],
    values?: Record<string, string | number>,
  ): string {
    this.translations.language();
    return this.translations.translate(key, values);
  }
}
