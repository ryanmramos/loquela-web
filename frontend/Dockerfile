# syntax=docker/dockerfile:1.7

FROM node:24.18.0-alpine AS web-build
ARG RELEASE_ID
ARG RAILWAY_GIT_COMMIT_SHA
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build \
    && LOQUELA_RELEASE_ID="${RELEASE_ID:-${RAILWAY_GIT_COMMIT_SHA:-development}}" \
       node tools/prepare-caddy-artifact.mjs

FROM caddy:2.11.4-builder AS caddy-build
RUN GOTOOLCHAIN=go1.26.5 xcaddy build v2.11.4 \
    --with golang.org/x/text@v0.39.0 \
    --with google.golang.org/grpc@v1.82.1

FROM caddy:2.11.4
COPY --from=caddy-build /usr/bin/caddy /usr/bin/caddy
RUN apk upgrade --no-cache
COPY frontend/Caddyfile /etc/caddy/Caddyfile
RUN caddy fmt --overwrite /etc/caddy/Caddyfile \
    && caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
COPY --from=web-build /workspace/frontend/dist/loquela-web/browser /srv
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1:${PORT:-8080}/ || exit 1
