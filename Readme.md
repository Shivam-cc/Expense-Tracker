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

## Mobile App (Planned)

Native iOS and Android apps are planned. They are not implemented yet.

**Approach:** native UIs (SwiftUI and Jetpack Compose) on a shared Kotlin Multiplatform (KMP) core. The core holds models, the API client, the local database, the sync engine, and auth/token logic. The existing Spring Boot API and MongoDB backend stay as they are, with the mobile-specific additions below.

| Layer | iOS | Android | Shared (KMP) |
|---|---|---|---|
| UI | SwiftUI, Swift Charts | Jetpack Compose, Material 3, Vico | - |
| Networking | - | - | Ktor Client, kotlinx.serialization, OpenAPI-generated models |
| Local DB | - | - | SQLDelight (outbox pattern for offline writes) |
| Secure storage | Keychain | Android Keystore | expect/actual wrappers |
| Platform features | WidgetKit, App Intents, VisionKit + Vision OCR, Face ID / Touch ID, APNs | Jetpack Glance, App Actions, ML Kit scanner + text recognition, BiometricPrompt, FCM | - |
| Background sync | BGTaskScheduler | WorkManager | Sync engine |
| Testing | XCTest / Swift Testing, XCUITest | JUnit, Compose UI Test | kotlin.test |

**Offline-first:** the local database is the source of truth for the UI. Writes go to a local outbox, and a sync engine pushes and pulls in the background. Records use client-generated UUIDv7 IDs. Conflicts use last-write-wins on server timestamps. Realm / Atlas Device Sync is not used, because MongoDB has deprecated it.

**Backend changes needed before the apps:**
1. Versioned API (`/api/v1/...`) so older app versions keep working.
2. Sync support: `GET /expenses?updatedSince=<ts>`, soft deletes (tombstones), `updatedAt` on every document, and an index on `(userId, updatedAt)`.
3. Idempotency keys on create and update requests, so network retries do not duplicate expenses.
4. Refresh tokens returned in the response body for native secure storage, with reuse detection, per-device sessions, and "sign out of all devices".
5. Device registration for push notifications (APNs and FCM tokens per device).
6. A minimum-supported-version endpoint so a security fix can force an upgrade.
7. Receipt images, if scanning is added, stored in object storage (S3/R2) through pre-signed uploads rather than in MongoDB.
8. Optional App Attest (iOS) and Play Integrity (Android) checks on auth endpoints.
9. Account deletion endpoint that removes the user and all their expenses. Required by both app stores.

**Suggested layout:**
```
mobile/
  shared/        KMP module: models, sync, database, networking
  androidApp/    Jetpack Compose app
  iosApp/        SwiftUI app consuming the shared framework
api/openapi.yaml exported spec used to generate web and mobile clients
```

**Build order:** backend changes → KMP core (heavily unit-tested) → auth, expense list/add, categories, and dashboard in both apps → platform extras (widgets, biometrics, receipt OCR, shortcuts, reminders) → TestFlight and Play closed testing.

## License
MIT
