import { inject, Injectable, signal } from '@angular/core';
import { MessageService } from 'primeng/api';

import { AlertTone } from '../alert/alert';

export interface ToastMessage {
  readonly id: number;
  readonly title: string;
  readonly message: string;
  readonly tone: AlertTone;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly primeMessages = inject(MessageService, { optional: true });
  private readonly nextId = signal(0);
  private readonly toastState = signal<readonly ToastMessage[]>([]);

  readonly messages = this.toastState.asReadonly();

  show(title: string, message: string, tone: AlertTone = 'info'): void {
    const id = this.nextId() + 1;
    this.nextId.set(id);
    this.toastState.update((messages) => [...messages, { id, title, message, tone }]);
    this.primeMessages?.add({
      key: 'loquela',
      severity: tone === 'danger' ? 'error' : tone === 'warning' ? 'warn' : tone,
      summary: title,
      detail: message,
      life: 5_000,
    });
  }

  dismiss(id: number): void {
    this.toastState.update((messages) => messages.filter((message) => message.id !== id));
  }
}
