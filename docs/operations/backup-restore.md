# Backup and restore

ExpenseFlow keeps all user data in a single PostgreSQL database hosted on Railway. This page covers the built-in
Railway backups, a manual `pg_dump` / `pg_restore` procedure, a restore drill, and an optional scheduled GitHub
Actions backup.

## 1. Railway Postgres backups

Railway offers volume backups for Postgres services (Project → Postgres service → **Backups** tab).

- Enable a **daily** (and optionally weekly) schedule. Backup availability and retention depend on your Railway plan, so
  check the current limits in the Railway docs before relying on them.
- Backups live inside Railway. Treat them as the first line of defence, and keep an off-platform copy (section 4) for
  account-level incidents such as a deleted project.
- A Railway restore creates a new volume/deployment state. Verify the app against it before cutting traffic over.

## 2. Manual backup with `pg_dump`

You need a PostgreSQL client whose **major version is the same as or newer than the server** (`pg_dump --version`).

Use the database's **public** connection string (Railway → Postgres → Variables → `DATABASE_PUBLIC_URL`). The
internal `DATABASE_URL` only resolves inside Railway's private network.

```bash
export DATABASE_URL='postgresql://USER:PASSWORD@HOST:PORT/DBNAME'

pg_dump --format=custom --no-owner --no-privileges \
  --file "expenseflow-$(date -u +%Y%m%dT%H%M%SZ).dump" "$DATABASE_URL"

# Sanity check: lists the contents without restoring anything
pg_restore --list expenseflow-*.dump | head
```

PowerShell equivalent:

```powershell
$env:DATABASE_URL = 'postgresql://USER:PASSWORD@HOST:PORT/DBNAME'
pg_dump --format=custom --no-owner --no-privileges --file "expenseflow-$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')).dump" $env:DATABASE_URL
```

Dumps contain personal and financial data. Store them encrypted (for example with `gpg --symmetric --cipher-algo AES256`)
and never commit them.

## 3. Restore with `pg_restore`

**Always restore into an empty scratch database first**, never straight over production.

```bash
# Create a scratch database (local Docker example)
docker run -d --name restore-test -e POSTGRES_PASSWORD=test -p 55432:5432 postgres:16
createdb -h localhost -p 55432 -U postgres expenseflow_restore   # password: test

# Restore
pg_restore --no-owner --no-privileges --exit-on-error \
  -h localhost -p 55432 -U postgres -d expenseflow_restore expenseflow-YYYYMMDDTHHMMSSZ.dump
```

To restore into the real production database after an incident:

1. Put the app into maintenance (scale the backend service to zero, or stop the Railway deployment) so nothing writes
   during the restore.
2. Take a fresh `pg_dump` of the current state, even if it is damaged.
3. Restore into an empty database (`--clean --if-exists` drops existing objects first, so use it deliberately):
   ```bash
   pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error -d "$DATABASE_URL" backup.dump
   ```
4. Start the backend, check `/actuator/health`, and sign in with a test account.

## 4. Optional: scheduled off-platform backup (GitHub Actions)

The workflow [`.github/workflows/db-backup.yml`](../../.github/workflows/db-backup.yml) dumps the database every Sunday
at 03:00 UTC (and on demand via **Actions → Database backup → Run workflow**), encrypts it with GPG (AES-256), and
uploads it as a workflow artifact kept for 30 days.

Set these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `DATABASE_URL` | The Railway **public** Postgres URL (`DATABASE_PUBLIC_URL`) |
| `BACKUP_PASSPHRASE` | A long random passphrase, stored in your password manager. Without it the backup cannot be decrypted |

If either secret is missing, the job prints a notice and finishes successfully without doing anything.

Things to know:

- GitHub artifacts are visible to anyone who can read the repository. Even though the dump is encrypted, prefer a
  **private repository** for this workflow, or change the last step to upload to storage you control (S3, Backblaze B2, etc.).
- Set `PG_CLIENT_VERSION` in the workflow to at least the Postgres major version on Railway.
- Decrypt a downloaded backup with:
  ```bash
  gpg --batch --pinentry-mode loopback --decrypt --output expenseflow.dump expenseflow-YYYYMMDDTHHMMSSZ.dump.gpg
  ```

## 5. Restore drill checklist

Run this at least once before launch and then every quarter. A backup that has never been restored is not a backup.

- [ ] Download or create a recent backup (Railway backup or the encrypted artifact) and decrypt it.
- [ ] Restore it into a scratch Postgres of the same major version (section 3).
- [ ] Row counts for `users`, `categories`, and `expenses` look plausible compared to production.
- [ ] Start the backend locally against the restored database (`SPRING_PROFILES_ACTIVE=prod`, see `backend/.env.example`).
- [ ] `/actuator/health` returns `UP`, and signing in with a known account lists its expenses.
- [ ] Record the date, backup age, time taken, and any problems below.
- [ ] Delete the scratch database and any decrypted dump files.

| Date | Backup used | Duration | Result / notes |
| --- | --- | --- | --- |
| _YYYY-MM-DD_ | | | |
