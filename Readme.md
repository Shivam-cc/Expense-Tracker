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
- **Spring Data JPA** with Flyway migrations (PostgreSQL in production, H2 locally)
- **Spring Security** with JWT (JJWT) and rotating refresh tokens
- **SendGrid** Web API for email verification codes
- **Spring Boot Actuator**, **Sentry** and **springdoc** OpenAPI docs
- **Maven** wrapper (`mvnw`) with JaCoCo coverage

## Project Structure

```
Expense-Tracker/
├── .github/workflows/      # CI (backend tests, frontend lint/test/build), CodeQL, DB backup
├── backend/                # Java Spring Boot API
│   ├── src/
│   │   ├── main/java/com/expensetracker/
│   │   │   ├── config/     # Security, JWT, rate limiting, request IDs, error handling
│   │   │   ├── controller/ # REST controllers (auth, expenses, categories)
│   │   │   ├── dto/        # Request and response objects
│   │   │   ├── exception/  # API exceptions
│   │   │   ├── model/      # JPA entities
│   │   │   ├── repository/ # Spring Data repositories
│   │   │   └── service/    # Business logic (auth, OTP, tokens, email, CSV export)
│   │   ├── main/resources/
│   │   │   ├── application*.properties
│   │   │   └── db/migration/ # Flyway schema migrations
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
- PostgreSQL 16+ only for the `prod` profile. Local development uses an in-memory H2 database.

### Local Development
Run the backend and frontend in two terminals.

**Backend** (http://localhost:8080):
```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```
The `local` profile is the default and needs no environment variables. It preconfigures H2, a dev-only JWT secret, and OTP dev mode, which logs verification codes at debug level instead of emailing them.

Useful local URLs:
- `http://localhost:8080/actuator/health`: health check
- `http://localhost:8080/swagger-ui.html`: API docs
- `http://localhost:8080/h2-console`: H2 console (JDBC URL `jdbc:h2:mem:expense_tracker`, user `sa`, empty password)

Local data is in memory and resets when the backend restarts.

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
cd backend
./mvnw verify

# Frontend: lint, unit tests, production build
cd frontend
npm run lint
npm test
npm run build
```
The GitHub Actions workflow in `.github/workflows/ci.yml` runs the same checks, plus `npm audit`.

### Configuration
Environment variables are documented in [backend/.env.example](backend/.env.example) and [frontend/.env.example](frontend/.env.example).

### Deployment
- **Backend (Railway):** `railway.toml` builds `backend/Dockerfile` and checks `/actuator/health`. Set `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (base64, at least 256 bits; generate with `openssl rand -base64 48`), and `CORS_ORIGINS` (your exact frontend origin). Optional: `SENDGRID_API_KEY`, `SENDGRID_FROM`, `SENTRY_DSN`.
- **Frontend (Vercel):** `frontend/vercel.json` handles SPA routing. Set `VITE_API_URL` to the backend's public URL. Optional: `VITE_SENTRY_DSN`.

## License
MIT
