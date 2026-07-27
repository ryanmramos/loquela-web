export const DEFAULT_AUTHENTICATED_ROUTE = '/app';

export function safeAuthenticatedReturnUrl(candidate: string | null | undefined): string {
  if (
    !candidate ||
    !candidate.startsWith('/app') ||
    candidate.startsWith('//') ||
    candidate.includes('\\') ||
    /[\r\n]/u.test(candidate)
  ) {
    return DEFAULT_AUTHENTICATED_ROUTE;
  }

  try {
    const parsed = new URL(candidate, 'https://loquela.invalid');
    return parsed.origin === 'https://loquela.invalid'
      ? `${parsed.pathname}${parsed.search}${parsed.hash}`
      : DEFAULT_AUTHENTICATED_ROUTE;
  } catch {
    return DEFAULT_AUTHENTICATED_ROUTE;
  }
}
