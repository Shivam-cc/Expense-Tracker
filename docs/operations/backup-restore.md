# MongoDB backup and restore

ExpenseFlow stores users, categories, expenses, hashed OTPs, refresh tokens, and numeric ID counters in MongoDB.
Persistence survives application restarts, but it does not replace backups.

## Production backups

Use an authenticated replica set with durable disks or MongoDB Atlas. Enable your provider's automated backups
and test restoring them before relying on them. Never expose the unauthenticated local Docker service publicly.
For a consistent snapshot while writes continue, prefer provider snapshots / point-in-time recovery.
The database-scoped `mongodump` procedure below does not guarantee a single cross-collection point in time:
pause application writes during a manual dump or schedule a maintenance window.

## Manual backup

Install MongoDB Database Tools compatible with your server. Supply the connection URI through your environment;
it must include the application's database name (normally `expense_tracker`).

```powershell
# Set MONGODB_URI using your secret manager or shell before running.
$archive = "expenseflow-$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')).archive.gz"
mongodump --uri="$env:MONGODB_URI" --gzip --archive="$archive"
if ($LASTEXITCODE -ne 0) { throw "MongoDB backup failed" }
```

Back up the **entire database**, including `_sequences`. Restoring documents without their ID counters can
cause future inserts to reuse existing numeric IDs.
Dumps contain personal and financial data: encrypt them, restrict access, and never commit them.

## Restore drill

Always restore into an empty **scratch database** first, never directly over production.

```powershell
# RESTORE_MONGODB_URI must point to a scratch replica set / Atlas database.
mongorestore --uri="$env:RESTORE_MONGODB_URI" --gzip --archive="expenseflow-TIMESTAMP.archive.gz" `
  --nsFrom="expense_tracker.*" --nsTo="expense_tracker_restore.*" --stopOnError
if ($LASTEXITCODE -ne 0) { throw "MongoDB restore failed" }
```

1. Compare document counts in `users`, `categories`, `expenses`, and `_sequences` with the source.
2. Start the backend against the restored database (`MONGODB_URI` with database `expense_tracker_restore`).
3. Check `/actuator/health`, sign in, list existing expenses, and create a new expense.
4. Restart the backend and confirm the same records remain.
5. Record the backup age, restore duration, and results.
6. Remove only the scratch database and decrypted archives when the drill is complete.

For production recovery, stop application writes, take a fresh backup of the damaged database, restore into a
new database, verify it, and then switch the backend URI. Do not overwrite the old database before verification.

## Optional encrypted GitHub Actions backups

The [database backup workflow](../../.github/workflows/db-backup.yml) runs every Sunday at 03:00 UTC and on demand.
Set repository secrets:

| Secret | Value |
| --- | --- |
| `MONGODB_URI` | Reachable authenticated MongoDB URI including the application's database name |
| `BACKUP_PASSPHRASE` | Long random encryption passphrase kept in your password manager |

If either secret is missing, the workflow reports that it skipped the backup. It uses `mongodump` from the
`mongo:8.0` image, encrypts the archive using GPG AES-256, and retains the artifact for 30 days.
Allow the runner to reach the database using your network policy; do not open database access to the world
just to enable backups. Prefer a private repository or upload backups to restricted storage.
For live, transaction-consistent backups use provider snapshots instead of this database-scoped dump.

Decrypt a downloaded archive before restoring:

```powershell
gpg --decrypt --output expenseflow.archive.gz expenseflow-TIMESTAMP.archive.gz.gpg
```

## Local volume

`docker compose down` retains the `mongodata` volume. `docker compose down -v` destroys it.
Historical PostgreSQL/H2 SQL migrations are not used by MongoDB and are not an import mechanism.
