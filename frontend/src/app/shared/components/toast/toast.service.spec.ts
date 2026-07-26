import { TestBed } from '@angular/core/testing';

import { ToastService } from './toast.service';

describe('ToastService', () => {
  it('adds and dismisses a safe notification', () => {
    const service = TestBed.inject(ToastService);

    service.show('Source ready', 'The source is available.', 'success');
    const [message] = service.messages();

    expect(message).toMatchObject({
      title: 'Source ready',
      message: 'The source is available.',
      tone: 'success',
    });

    service.dismiss(message?.id ?? -1);
    expect(service.messages()).toEqual([]);
  });
});
