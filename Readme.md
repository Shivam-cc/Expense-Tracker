# ExpenseFlow - Smart Expense Tracker

A full-stack expense tracking application built with **React 19** (frontend) and **Java Spring Boot** (backend).

## Tech Stack

### Frontend
- **React 19** with Vite 8
- **Tailwind CSS 4** for styling
- **Framer Motion** for animations
- **React Router 7** for navigation
- **Recharts** for dashboard charts
- **Lucide React** for icons
- **Sentry** (optional) and **vite-plugin-pwa** for error reporting and installable offline support
- **Vitest** and **Testing Library** for tests

### Backend
- **Java 21** with Spring Boot 3.4
- **Spring Data MongoDB** with persistent storage, indexes, and multi-document transactions
- **Spring Security** with JWT (JJWT) and rotating refresh tokens
- **SendGrid** Web API for email verification codes
- **Spring Boot Actuator**, **Sentry** and **springdoc** OpenAPI docs
- **Maven** wrapper (`mvnw`) with JaCoCo coverage

### Sync API (v1)
Mobile clients sync expenses and categories through `/api/v1/expenses` and `/api/v1/categories`. The API provides
change feeds with cursor pagination (`/changes`), idempotent creates by `clientId`, last-write-wins updates, and soft
deletes (tombstones with `deletedAt`). Global categories are read-only. The existing `/api/*` endpoints are unchanged.
See [Mobile sync API v1](docs/api/mobile-sync-v1.md) for the full contract, examples, and error behavior.

## Project Structure

```
Expense-Tracker/
├── .github/workflows/      # CI (backend tests, frontend lint/test/build), CodeQL, DB backup
├── backend/                # Java Spring Boot API
│   ├── src/
│   │   ├── main/java/com/expensetracker/
│   │   │   ├── config/     # Security, JWT, rate limiting, request IDs, error handling
│   │   │   ├── controller/ # REST controllers (auth, expenses, categories; v1 sync API in controller/v1/)
│   │   │   ├── dto/        # Request and response objects
│   │   │   ├── exception/  # API exceptions
│   │   │   ├── model/      # MongoDB documents
│   │   │   ├── repository/ # Spring Data repositories
│   │   │   └── service/    # Business logic (auth, OTP, tokens, email, CSV export)
│   │   ├── main/resources/
│   │   │   ├── application*.properties
│   │   │   └── db/migration/ # Historical SQL migrations (not executed by MongoDB)
│   │   └── test/          # Unit and integration tests
│   ├── Dockerfile
│   ├── .env.example
│   └── pom.xml
├── frontend/               # React + Vite application
│   ├── src/
│   │   ├── components/     # UI, dashboard, landing, layout, legal, toast
│   │   ├── context/        # Auth state
│   │   ├── hooks/          # Custom hooks (expense filters, async data)
│   │   ├── pages/          # Route-level pages
│   │   ├── services/       # API client with token refresh
│   │   ├── test/           # Test setup
│   │   └── utils/          # Formatting, dates, downloads, forms
│   ├── .env.example
│   ├── package.json
│   └── vercel.json
├── railway.toml            # Railway deployment (backend Docker build)
└── Readme.md
```

## Getting Started

### Prerequisites
- Node.js 22 (CI uses 22; 20+ should work)
- JDK 21, with `JAVA_HOME` pointing to it (the Maven wrapper is included)
- Docker Desktop for local MongoDB, or access to MongoDB Atlas / a MongoDB replica set.
- MongoDB 8.0 is used by the local Docker stack. A standalone `mongod` is insufficient: authentication and account operations require transactions.

### Local Development
Run the backend and frontend in two terminals.

Start MongoDB once from the repository root:
```powershell
docker compose up -d --wait mongodb
```
MongoDB stores data in the `mongodata` named volume. Backend restarts, MongoDB restarts, and `docker compose down` preserve it.
**Do not use `docker compose down -v`** unless you intend to delete the database.
The local database has no authentication and is exposed only on `127.0.0.1`; do not deploy this local setup publicly.

**Backend** (http://localhost:8080):
```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```
The `local` profile is the default. It connects to `mongodb://localhost:27017/expense_tracker?replicaSet=rs0&directConnection=true`
and preconfigures a dev-only JWT secret and OTP dev mode (verification codes returned in API responses).
Override `MONGODB_URI` to use another replica set or Atlas; include the database name in the URI.
Indexes and the 16 default categories are initialized idempotently at startup. The numeric API ID format is preserved.

Useful local URLs:
- `http://localhost:8080/actuator/health`: health check
- `http://localhost:8080/swagger-ui.html`: API docs
Browse the database with MongoDB Compass using the local connection URI above.

Alternatively, run both MongoDB and the backend with `docker compose up --build -d`. This is a local development
stack with development OTPs enabled, not a production deployment.

### Existing data
This switches persistence from H2/PostgreSQL to MongoDB; it does **not** import existing SQL records.
Historical SQL migrations are retained only for reference and are no longer run.
In-memory H2 data that was already lost on shutdown cannot be recovered. Back up any existing persistent SQL
database before switching. MongoDB starts with a new database and seeded categories; existing SQL accounts must
be registered again unless you arrange a separate data import.

**Frontend** (http://localhost:5173):
```bash
cd frontend
npm install
npm run dev
```
The frontend calls `http://localhost:8080` by default. To use another backend, set `VITE_API_URL` in `frontend/.env.local` (see `frontend/.env.example`).

### Tests and Checks
```bash
# Backend: compiles and runs all tests; JaCoCo report in backend/target/site/jacoco
# Start MongoDB first: docker compose up -d --wait mongodb (from repository root)
cd backend
./mvnw verify

# Frontend: lint, unit tests, production build
cd frontend
npm run lint
npm test
npm run build
```
The GitHub Actions workflow in `.github/workflows/ci.yml` runs the same checks, plus `npm audit`.
Backend integration tests use a unique `expense_tracker_test_*` database, never the application's database.
Set `TEST_MONGODB_URI` to test against a different replica set. Test databases can be removed after the run.

### Configuration
Environment variables are documented in [backend/.env.example](backend/.env.example) and [frontend/.env.example](frontend/.env.example).

### Deployment
- **Backend (Railway):** `railway.toml` builds `backend/Dockerfile` and checks `/actuator/health`. Set `SPRING_PROFILES_ACTIVE=prod`, `MONGODB_URI` (authenticated Atlas or replica-set URI including the database name), `JWT_SECRET` (base64, at least 256 bits; generate with `openssl rand -base64 48`), and `CORS_ORIGINS` (your exact frontend origin). Optional: `SENDGRID_API_KEY`, `SENDGRID_FROM`, `SENTRY_DSN`. Configure database access controls, durable storage, and backups; see [backup and restore](docs/operations/backup-restore.md).
- **Frontend (Vercel):** `frontend/vercel.json` handles SPA routing. Set `VITE_API_URL` to the backend's public URL. Optional: `VITE_SENTRY_DSN`.

## Mobile App

Status: the Kotlin Multiplatform shared core is implemented under [mobile/shared](mobile/shared/). The SwiftUI and Jetpack Compose apps are **not built yet**.

**Shared core (`mobile/shared`):** models, the Ktor API client, the SQLDelight local database with an outbox, the sync engine, auth/token handling (Android Keystore AES-GCM via DataStore), and money handling in integer minor units. The wire contract is documented in [Mobile sync API v1](docs/api/mobile-sync-v1.md).

**Run the shared-core tests (from `mobile/`):**
```powershell
.\gradlew.bat :shared:jvmTest :shared:compileDebugKotlinAndroid   # Windows
./gradlew :shared:jvmTest :shared:compileDebugKotlinAndroid       # macOS/Linux
```
Android builds need the Android SDK (`ANDROID_HOME` or `mobile/local.properties`). iOS targets compile on macOS only; the CI job `mobile-ios` covers them.

**End-to-end test against the local backend:** `SyncE2ETest` (in `mobile/shared/src/jvmTest/kotlin/com/expensetracker/mobile/e2e/`) registers a user, reads the OTP from the local profile, signs in, makes offline edits, syncs across two devices, checks exact amounts, and verifies token rotation. It is skipped when the backend is not reachable.
```powershell
docker compose up -d --wait mongodb                                  # repository root
cd backend; .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.profiles=local,e2e"   # leave running
cd ..\mobile; .\gradlew.bat :shared:cleanJvmTest :shared:jvmTest --no-build-cache --tests "com.expensetracker.mobile.e2e.SyncE2ETest"
```
The `e2e` profile (`backend/src/main/resources/application-e2e.properties`) raises only the auth rate limit (`app.rate-limit.auth-max=1000`), because the suite makes more auth calls than the default 15 per minute allows. Run the backend with `local` alone for normal development, which keeps the default limit. The base URL defaults to `http://localhost:8080` and can be changed with `EXPENSEFLOW_API_URL`. Use `--no-build-cache` (and `cleanJvmTest`) to force a live run; otherwise Gradle may report cached results without contacting the backend.

**Planned (not yet built):**
- Native UIs: SwiftUI with Swift Charts, WidgetKit, App Intents, and Vision OCR on iOS; Jetpack Compose with Material 3, Vico, Glance, and ML Kit on Android.
- Platform features: biometrics, push (APNs and FCM), background sync (BGTaskScheduler and WorkManager), shortcuts, and receipt scanning.
- Shared-core remaining work: a token-migration path for existing sessions (the old tokens are discarded, so users sign in again after upgrading), and the tombstone purge job (proposal only, see the sync doc).

**Backend for mobile:** the versioned sync API under `/api/v1` is implemented, with soft deletes (tombstones), idempotent creates by `clientId`, and integer minor-unit amounts. Refresh tokens rotate with reuse detection. Account deletion is a hard delete at `DELETE /api/users/me`. Not yet built: a minimum-supported-version endpoint and device registration for push. See [Mobile sync API v1](docs/api/mobile-sync-v1.md).

## License
MIT
