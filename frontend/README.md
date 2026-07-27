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
npm run architecture:check # import, storage, and sanitizer boundaries
npm run proxy:check        # live local proxy smoke test with an isolated backend
npm run api:generate       # validate the contract and regenerate the Angular client
npm run api:check          # incompatible-schema and deterministic-drift checks
npm run e2e               # Playwright; installs no browser automatically
npm run audit:dependencies
npm run licenses:check
```

Install the Playwright Chromium runtime once before local end-to-end tests:

```bash
npx playwright install chromium
```

## OpenAPI client

The Angular client generator is pinned and configured in `ng-openapi-gen.json`. From the repository
root, intentionally refresh Spring's committed browser contract after reviewing a backend API
change:

```bash
./mvnw -Dtest=BrowserOpenApiContractIntegrationTest \
  -Dloquela.openapi.export=true test
```

Then regenerate and validate the client:

```bash
cd frontend
npm run api:generate
npm run api:check
```

Generated code belongs under `src/app/api/generated` and must never be edited manually. The
application accesses it through stable adapters under `src/app/api/api-adapters`. The generator
decision is recorded in
[`../docs/frontend/ADR-001-OPENAPI_CLIENT_GENERATOR.md`](../docs/frontend/ADR-001-OPENAPI_CLIENT_GENERATOR.md).

## Runtime configuration and local API

Angular loads `public/config.json` before bootstrap. This file is public and must never contain
credentials. Missing or invalid required values stop bootstrap and display a safe static error.

The development server proxies `/api`, `/actuator/health`, `/oauth2`, and `/login/oauth2` to
`http://127.0.0.1:8080`. Override that target temporarily with `LOQUELA_BACKEND_URL`. Application
API requests use the same-origin `/api/v1` base path and include the server session and Spring's
`XSRF-TOKEN`/`X-XSRF-TOKEN` convention.

## Sign-in and local accounts

The `/login` page redirects Google sign-in to `/api/v1/auth/google`; Spring Security owns the OIDC
exchange and only the safe session DTO returns to Angular. `SessionFacade` shares one session load,
guards authenticated/deleting/anonymous routes, handles expiration, and clears memory after the
CSRF-protected logout operation.

For local development, `public/config.json` displays **Local Learner** and **Local Reviewer**.
These buttons call the backend's profile- and property-gated local login boundary and create normal
users and JDBC sessions in the local database. Copy the repository `.env.example` and run the
backend with its `local` profile. Production runtime configuration rejects this UI capability, and
the backend production validator rejects the corresponding server flag.

## Architecture controls

`npm run architecture:check` prevents feature-to-feature deep imports, shared-to-feature imports,
direct generated-client imports from components, browser storage outside the reviewed adapter,
unsafe Angular sanitizer bypass calls, and generated API files without a generator marker.
Type-aware ESLint rules additionally reject unsafe `any` flows and floating promises.

## Design foundation

The local/test-only `/foundation` route exercises the maintained shared component set and the
responsive application shell. Semantic color, typography, spacing, shape, elevation, breakpoint,
motion, and layer roles live under `src/styles/`; component styles consume those roles instead of
copying values from the original static mockups.

End-to-end checks run Axe accessibility scanning, keyboard focus flows, reduced-motion emulation,
and layout checks at 320 px, 768 px, and 1440 px. Shared controls live under
`src/app/shared/components`, while the application shell remains under `src/app/core/layout`.

## Build cache

The Angular CLI persistent disk cache is disabled in `angular.json`. With the pinned Node 24
runtime on macOS, the cache's native LMDB binding can abort during optimized builds. Disabling
that optional cache keeps local and CI production builds deterministic; it does not change build
output.
