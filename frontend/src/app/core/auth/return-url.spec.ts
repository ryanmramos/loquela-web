import { safeAuthenticatedReturnUrl } from './return-url';

describe('safeAuthenticatedReturnUrl', () => {
  it('accepts relative authenticated routes with query and fragment values', () => {
    expect(safeAuthenticatedReturnUrl('/app/decks?sort=recent#ready')).toBe(
      '/app/decks?sort=recent#ready',
    );
  });

  it.each([
    undefined,
    null,
    '',
    '/login',
    '//evil.example/app',
    'https://evil.example/app',
    '/app\\evil',
    '/app\r\nLocation: https://evil.example',
  ])('falls back for unsafe return URL %s', (value) => {
    expect(safeAuthenticatedReturnUrl(value)).toBe('/app');
  });
});
