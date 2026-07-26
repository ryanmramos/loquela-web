# Loquela web

Angular 22 client for Loquela. The frontend is kept in this repository but has an independent
Node/npm build lifecycle from the Spring Boot backend.

## Prerequisites

- Node `24.18.0` (pinned in `.nvmrc`)
- npm `11.16.x`

With nvm:

```bash
nvm install
nvm use
npm ci
```

## Commands

```bash
npm start                 # local Angular development server
npm run build             # production build
npm test -- --watch=false # Vitest unit/component tests
npm run lint
npm run format:check
npm run e2e               # Playwright; installs no browser automatically
npm run audit:dependencies
npm run licenses:check
```

Install the Playwright Chromium runtime once before local end-to-end tests:

```bash
npx playwright install chromium
```

## OpenAPI client

The Angular client generator is pinned and configured in `ng-openapi-gen.json`. Once the backend
contract export is added in Step 6:

```bash
npm run api:generate
```

Generated code belongs under `src/app/api/generated` and must never be edited manually. The
generator decision is recorded in
[`../docs/frontend/ADR-001-OPENAPI_CLIENT_GENERATOR.md`](../docs/frontend/ADR-001-OPENAPI_CLIENT_GENERATOR.md).

## Build cache

The Angular CLI persistent disk cache is disabled in `angular.json`. With the pinned Node 24
runtime on macOS, the cache's native LMDB binding can abort during optimized builds. Disabling
that optional cache keeps local and CI production builds deterministic; it does not change build
output.
