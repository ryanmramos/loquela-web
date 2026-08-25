# Loquela

Loquela has a Spring Boot API and an Angular web application. PostgreSQL stores application data.
MinIO provides S3-compatible object storage for local development.

## Prerequisites

- Java 17
- Docker with Docker Compose
- Node.js `24.18.0` and npm `11.16.x`
- Optional: Python 3.11+ for Anki package exports
- Optional: `yt-dlp` for YouTube subtitle ingestion

The Maven wrapper is included, so a separate Maven installation is not required. The frontend's
Node version is pinned in `frontend/.nvmrc`.

## Initial setup

From the repository root, create your local configuration and start PostgreSQL and MinIO:

```bash
cp .env.example .env
docker compose up -d
```

The values in `.env.example` are for local development. Keep `.env` out of Git. Store real
credentials only in `.env` or another secure secret store.

The local configuration uses Angular's same-origin development proxy. Keep CORS blank for this
workflow:

```dotenv
WHATSANKI_ALLOWED_ORIGINS=
```

### Local test accounts

The `.env.example` enables the `local` Spring profile and the local-only login flow. Start both
applications. Open `http://localhost:4200/login` and choose **Local Learner**, **Local Reviewer**,
or **Local Admin**. The first login for each local account creates a normal Google-only application
user, a default deck, and a JDBC browser session in the local PostgreSQL database. The flow does not
contact Google or require a phone number.

The local login endpoint is available only when both `SPRING_PROFILES_ACTIVE=local` and
`WHATSANKI_LOCAL_LOGIN_ENABLED=true` are set. Production startup rejects local login.

### Google OAuth

Google OAuth is disabled in the local example. To exercise the real provider flow, create a Google
OAuth web client, register `http://localhost:4200/login/oauth2/code/google` as an authorized
redirect URI, and set:

```dotenv
WHATSANKI_GOOGLE_OAUTH_ENABLED=true
WHATSANKI_FRONTEND_BASE_URL=http://localhost:4200
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=your-client-id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=your-client-secret
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=http://localhost:4200/login/oauth2/code/google
WHATSANKI_BOOTSTRAP_ADMIN_EMAILS=owner@example.com
```

Google sign-in uses an allowlist. On the first deployment, `WHATSANKI_BOOTSTRAP_ADMIN_EMAILS` seeds
one or more exact, normalized email addresses as persistent administrators. After an active
administrator exists in PostgreSQL, the application ignores the environment value. Manage access at
`/app/admin/access` without restarting the application. Remove the bootstrap value after you verify
that the first administrator can sign in. Local test-account login remains independent of this list.

You can start the backend without Twilio credentials if you are not testing WhatsApp integration.

### Optional Anki exporter setup

Anki package export uses the Python helper under `exporter/`. Create and activate its virtual
environment before starting the backend:

```bash
python3 -m venv exporter/.venv
source exporter/.venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -e exporter
```

The backend runs `python3` by default, so it uses the active virtual environment. This setup is not
required for features that do not export `.apkg` files.

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

Open `http://localhost:4200`. If you use another Node version manager, install the Node.js and npm
versions listed in the prerequisites before running `npm ci`.

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

This command preserves the PostgreSQL and MinIO named volumes. To inspect the running services, use
`docker compose ps`.
