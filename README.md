# Loquela

Loquela combines a Spring Boot API with an independently built Angular web application. PostgreSQL
stores application data, and MinIO provides S3-compatible object storage for local development.

## Prerequisites

- Java 17
- Docker with Docker Compose
- Node.js `24.18.0` and npm `11.16.x`
- Optional: Python 3.11+ for Anki package exports
- Optional: `yt-dlp` for YouTube subtitle ingestion

The Maven wrapper is included, so a separate Maven installation is not required. The frontend's
Node version is pinned in `frontend/.nvmrc`.

## Initial setup

From the repository root, create your private local configuration and start PostgreSQL and MinIO:

```bash
cp .env.example .env
docker compose up -d
```

The checked-in defaults are intended only for local development. Keep `.env` out of Git and place
real credentials only in that file or another secure secret store.

To allow the Angular development server to call the backend, set this value in `.env`:

```dotenv
WHATSANKI_ALLOWED_ORIGINS=http://localhost:4200
```

Google OAuth is disabled by default. To exercise sign-in locally, create a Google OAuth web client,
register `http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI, and set:

```dotenv
WHATSANKI_GOOGLE_OAUTH_ENABLED=true
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=your-client-id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=your-client-secret
```

The backend can start without Twilio credentials when WhatsApp integration is not being exercised.

### Optional Anki exporter setup

Anki package export uses the Python helper under `exporter/`. Create and activate its virtual
environment before starting the backend:

```bash
python3 -m venv exporter/.venv
source exporter/.venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -e exporter
```

Because the backend launches `python3` by default, it will inherit the activated virtual
environment. This setup is not required for features that do not export `.apkg` files.

## Run the backend

With PostgreSQL and MinIO running, start Spring Boot from the repository root:

```bash
./mvnw spring-boot:run
```

The backend loads `.env` automatically and is available at:

- API: `http://localhost:8080`
- Health check: `http://localhost:8080/actuator/health`
- OpenAPI document: `http://localhost:8080/v3/api-docs`
- MinIO console: `http://localhost:9001`

Run the backend test suite with:

```bash
./mvnw test
```

## Run the frontend

In a second terminal:

```bash
cd frontend
nvm install
nvm use
npm ci
npm start
```

Open `http://localhost:4200`. If you do not use `nvm`, install the Node and npm versions listed in
the prerequisites before running `npm ci`.

Useful frontend checks:

```bash
npm run verify
npm run e2e
```

Playwright requires a browser runtime before the first end-to-end test run:

```bash
npx playwright install chromium
```

## Stop local infrastructure

```bash
docker compose down
```

This preserves the PostgreSQL and MinIO named volumes. To inspect the running services at any time,
use `docker compose ps`.
