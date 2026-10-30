# Mobile sync API v1

This document is the contract for the versioned sync API that mobile clients use to keep expenses and categories
in step with the server. Sync resources live only under `/api/v1`. The existing `/api/*` endpoints and
`/api/auth/*` are unchanged.

## Conventions

**Authentication.** Send `Authorization: Bearer <access token>` on every request. Requests with a missing or invalid
token are rejected by the existing authentication layer.

**Timestamps.** `updatedAt`, `deletedAt`, `createdAt`, `serverTime`, and `updatedSince` are ISO-8601 UTC instants,
for example `2026-10-29T10:00:00Z`. Values may include fractional seconds, for example `2026-10-29T10:00:00.120Z`.
Clients must parse them as instants.

**Dates.** `date` is an ISO local date (`yyyy-MM-dd`).

**Amounts.** Money is never a float on the wire. An expense carries `amountMinor`, a JSON integer in the minor unit of
its `currency` (ISO 4217). `amountMinor: 1250` with `currency: "USD"` is 12.50 USD. `amountMinor: 1250` with `"JPY"` is
1250 yen, because JPY has 0 minor digits. `"KWD"` has 3 digits, so `1250` is 1.250 KWD. Clients convert with the exponent
of the ISO currency, never with floating-point math. `currency` is required on every write and is always returned.
Rows written before v1 (by the web app) have no currency. A startup backfill sets it to the owner's currency, so `/api/v1`
never returns `null`. A legacy amount with more decimal places than its currency allows is rounded HALF_UP and logged at
WARN. It is not rejected, so the server log is the place to review those rows.

**Errors.** Every error uses the standard error body:

```json
{
  "timestamp": "2026-10-29T10:00:00.000",
  "status": 404,
  "error": "Not Found",
  "message": "Expense not found",
  "path": "/api/v1/expenses/42"
}
```

Validation failures (400) add an `errors` object that maps each field to a message.

| Status | Meaning in v1 |
|---|---|
| 400 | Invalid body or parameter: missing or non-UUID `clientId`, failed field validation, `limit` outside 1..500, unparseable `updatedSince`, or invalid `cursor`. |
| 401 | Rejected by the existing authentication layer when the access token is missing or invalid. |
| 401 `error: "account_deleted"` | Returned by `/api/auth/refresh` when the refresh token belonged to a deleted account. Distinct from an expired or revoked session. Clients wipe local data. See Notes for clients. |
| 403 | Attempt to change a global (built-in) category with PUT or DELETE. |
| 404 | Record does not exist, belongs to another user, or is a tombstone (for PUT), or a referenced category is missing, another user's, or deleted. |
| 409 | Category name matches a built-in category (case-insensitive). The body includes `existingCategory` (the built-in `CategoryV1`). Clients adopt it instead of retrying. See Notes for clients. |

## Data model

### ExpenseV1 (response)

```json
{
  "id": 42,
  "clientId": "3f1c2a7e-8b4d-4e5f-9a6b-7c8d9e0f1a2b",
  "description": "Coffee",
  "amountMinor": 450,
  "currency": "USD",
  "date": "2026-10-28",
  "categoryId": 7,
  "categoryName": "Food",
  "notes": null,
  "createdAt": "2026-10-28T09:15:00Z",
  "updatedAt": "2026-10-28T09:15:00Z",
  "deletedAt": null
}
```

| Field | Type | Notes |
|---|---|---|
| `id` | long | Server id. Use it for PUT and DELETE. |
| `clientId` | string or null | Client-generated UUID. Stored in lower case. |
| `description` | string | |
| `amountMinor` | long | Integer minor units of `currency`. Never negative in practice (writes require positive). |
| `currency` | string | ISO 4217 code, upper case. Set by the write. Legacy rows without a currency are backfilled to the owner's currency at startup, so this is never null. |
| `date` | string | `yyyy-MM-dd` |
| `categoryId` | long or null | Null when uncategorized, or after the category was deleted. |
| `categoryName` | string or null | Name of the referenced category. |
| `notes` | string or null | |
| `createdAt` | instant or null | Null for rows written before timestamps were recorded. |
| `updatedAt` | instant | Set by the server on every write, including deletes and category cascades. |
| `deletedAt` | instant or null | Non-null on a tombstone. |

### ExpenseV1Request (body for POST and PUT)

```json
{
  "clientId": "3f1c2a7e-8b4d-4e5f-9a6b-7c8d9e0f1a2b",
  "description": "Coffee",
  "amountMinor": 450,
  "currency": "USD",
  "date": "2026-10-28",
  "categoryId": 7,
  "notes": null
}
```

| Field | Rules |
|---|---|
| `clientId` | Required on POST, must be a UUID. Ignored on PUT. |
| `description` | Required, at most 255 characters. |
| `amountMinor` | Required, a JSON integer, from 1 to 999999999999. Interpreted in `currency` minor units. Strings and decimals are rejected. |
| `currency` | Required, a known ISO 4217 code. Trimmed and upper-cased by the server, so `" usd "` is stored as `USD`. Unknown codes return `400`. |
| `date` | Required, `yyyy-MM-dd`. |
| `categoryId` | Optional. Must be one of the caller's live categories or a live global category. |
| `notes` | Optional, at most 1000 characters. |

### CategoryV1 (response)

```json
{
  "id": 7,
  "clientId": null,
  "name": "Food",
  "icon": "utensils",
  "color": "#f97316",
  "global": true,
  "updatedAt": "2026-10-01T00:00:00Z",
  "deletedAt": null
}
```

| Field | Type | Notes |
|---|---|---|
| `id` | long | |
| `clientId` | string or null | Null for global categories. |
| `name` | string | |
| `icon` | string or null | |
| `color` | string or null | |
| `global` | boolean | `true` for built-in categories. These are read-only. |
| `updatedAt` | instant | |
| `deletedAt` | instant or null | Non-null on a tombstone. |

### CategoryV1Request (body for POST and PUT)

```json
{
  "clientId": "9b2e1d4c-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "name": "Travel",
  "icon": "plane",
  "color": "#3b82f6"
}
```

| Field | Rules |
|---|---|
| `clientId` | Required on POST, must be a UUID. Ignored on PUT. |
| `name` | Required, at most 50 characters. |
| `icon` | Optional, at most 16 characters. |
| `color` | Optional, hex like `#3b82f6`. |

### Changes page

```json
{
  "items": [ ],
  "nextCursor": "MTc4MDA4MDgwMDAwMHwxMjM",
  "hasMore": true,
  "serverTime": "2026-10-29T10:00:05Z"
}
```

`nextCursor` is `null` when `hasMore` is `false`. `serverTime` is read before the page is queried.

## Change feeds

### GET /api/v1/expenses/changes

Returns the caller's expenses, including tombstones (rows with `deletedAt` set).

Query parameters:

| Name | Required | Default | Behavior |
|---|---|---|---|
| `updatedSince` | no | none | Returns rows with `updatedAt >= updatedSince`. The comparison is inclusive. Omitted means start from the beginning. |
| `cursor` | no | none | Opaque value from a previous `nextCursor`. When present, `updatedSince` is ignored. |
| `limit` | no | 200 | Integer 1..500. Anything else returns 400. |

Ordering is `updatedAt` ascending, then `id` ascending. The `cursor` encodes the last returned row's
`(updatedAt, id)`. Clients must treat it as opaque. Internally it is base64url of `"<epoch millis>|<id>"`, which is
an implementation detail.

Response: `200 OK` with a changes page of `ExpenseV1` items.

```
GET /api/v1/expenses/changes?updatedSince=2026-10-28T00:00:00Z&limit=2
```

```json
{
  "items": [
    { "id": 42, "clientId": "3f1c2a7e-8b4d-4e5f-9a6b-7c8d9e0f1a2b", "description": "Coffee", "amountMinor": 450,
      "currency": "USD", "date": "2026-10-28", "categoryId": 7, "categoryName": "Food", "notes": null,
      "createdAt": "2026-10-28T09:15:00Z", "updatedAt": "2026-10-28T09:15:00Z", "deletedAt": null },
    { "id": 43, "clientId": "0c1d2e3f-4a5b-4c6d-8e7f-900112233445", "description": "Old lunch", "amountMinor": 1200,
      "currency": "USD", "date": "2026-10-20", "categoryId": null, "categoryName": null, "notes": null,
      "createdAt": "2026-10-20T12:00:00Z", "updatedAt": "2026-10-28T09:20:00Z", "deletedAt": "2026-10-28T09:20:00Z" }
  ],
  "nextCursor": "MTc4MDA2NDAwMDAwMHw0Mw",
  "hasMore": true,
  "serverTime": "2026-10-29T10:00:05Z"
}
```

### GET /api/v1/categories/changes

Same query parameters, ordering, and response shape, with `CategoryV1` items. The feed includes the caller's
categories and all global categories (`global: true`), so clients receive the built-in categories through this feed.

### Paging and incremental sync

1. Full or first sync: call the feed with no parameters. While `hasMore` is `true`, call again with `cursor` set to
   `nextCursor`. The `updatedSince` value is not sent on these calls.
2. Incremental sync: store `serverTime` from the last completed sync and send it as `updatedSince` next time.
   Because the comparison is inclusive, the boundary row can come back. Clients must de-duplicate by `id`.
3. Writes that commit a moment after `serverTime` could be missed by a sync that started just before them. To avoid
   this, clients should start the next incremental sync a few seconds before the stored `serverTime`. Rows
   returned twice are harmless.
4. Tombstones (`deletedAt` set) must be applied locally. Remove the row and keep no live copy.

## Expenses

### POST /api/v1/expenses

Creates an expense. The request is idempotent by `clientId` for the caller.

- `201 Created` with `ExpenseV1` when the expense is new.
- `200 OK` with the existing `ExpenseV1` when an expense with the same `clientId` already exists for this user. The
  stored record is returned unchanged, even when the request body differs. This includes a tombstone, which is
  returned as is with `deletedAt` set.
- `400` when `clientId` is missing or not a UUID, or other fields fail validation.
- `404` when `categoryId` does not refer to a usable category (another user's, deleted, or unknown).

```
POST /api/v1/expenses
{ "clientId": "3f1c2a7e-8b4d-4e5f-9a6b-7c8d9e0f1a2b", "description": "Coffee", "amountMinor": 450,
  "currency": "USD", "date": "2026-10-28", "categoryId": 7, "notes": null }
```

Response `201` on first use, `200` on any retry with the same `clientId`. The body is an `ExpenseV1` object.

### PUT /api/v1/expenses/{id}

Replaces the mutable fields of the expense: `description`, `amountMinor`, `currency`, `date`, `categoryId`, and `notes`. Omitted
optional fields are set to null. The `clientId` in the body is ignored, so it may be omitted.

- Last write wins. The server sets `updatedAt`.
- `200 OK` with the updated `ExpenseV1`.
- `404` when the expense does not exist, belongs to another user, or is a tombstone. A tombstone can never be
  edited or resurrected through this endpoint.
- `400` for validation failures, and `404` when `categoryId` is not usable.

### DELETE /api/v1/expenses/{id}

Soft delete. Sets `deletedAt` and `updatedAt`. The row stays in the database so that the change feed can report it.

- `204 No Content` on success.
- Idempotent. Deleting an expense that is already a tombstone returns `204` again without changing it.
- `404` when the expense does not exist or belongs to another user.

## Categories

### POST /api/v1/categories

Creates a category. Idempotent by `clientId` for the caller, with the same rules as expenses.

- `201 Created` with `CategoryV1` for a new category.
- `200 OK` with the existing `CategoryV1` when `clientId` already exists for this user, including a tombstone. The
  stored record is returned unchanged.
- `400` for a missing or non-UUID `clientId`, or other validation failures.
- `409 Conflict` when `name` matches a live built-in category, case-insensitively. The body includes
  `existingCategory`, the built-in `CategoryV1` (with its server `id`). Clients adopt that ID: they remap local expenses and
  outbox rows to it and drop the local duplicate. See Notes for clients.

### PUT /api/v1/categories/{id}

Replaces `name`, `icon`, and `color`. The `clientId` in the body is ignored.

- `200 OK` with the updated `CategoryV1`. Last write wins.
- `403 Forbidden` for a global (built-in) category. Built-in categories are read-only, matching the existing
  `PUT /api/categories/{id}`.
- `404` when the category does not exist, belongs to another user, or is a tombstone.
- `409` when the new name matches a live built-in category.

### DELETE /api/v1/categories/{id}

Soft delete of a category owned by the caller.

- `204 No Content` on success.
- Expenses that reference the category are not deleted. Their `categoryId` is cleared and their `updatedAt` is set to
  the deletion time, so clients receive the change through the expense feed. Only live expenses are changed,
  matching the existing category delete.
- Idempotent. Deleting a category that is already a tombstone returns `204` again.
- `403` for a global category. These are read-only, matching the existing `DELETE /api/categories/{id}`.
- `404` when the category does not exist or belongs to another user.

## Tombstones and idempotency rules

- **Tombstones.** A soft-deleted expense or category keeps its `id`, `clientId`, and other fields, and gets `deletedAt`
  and a new `updatedAt`. Tombstones appear in change feeds so that clients can remove their local copies.
- **Create idempotency.** `clientId` is unique per user for expenses and categories. A retried POST returns the
  original record, with status `200` instead of `201`. The record is not modified.
- **Tombstones are final.** Once deleted, an expense or category cannot be changed with PUT (404). Create with the same
  `clientId` returns the tombstone (200). To re-create, the client must generate a new `clientId`.
- **Delete idempotency.** Repeated DELETE on the same id returns 204.
- **Last write wins.** PUT replaces the fields and sets `updatedAt` to the server time. The server does not merge.
- **Global categories.** These have no owner and `clientId` is null. They are read-only, and PUT/DELETE return 403.

### Tombstone retention (proposed, not implemented)

Tombstones cannot be purged while a client might still depend on them. A client that was offline longer than the
retention window would otherwise keep rows the server has forgotten. This is a proposal for review. No purge job exists.

- **Window.** Keep a tombstone for 180 days after `deletedAt`. After that, it may be hard-deleted.
- **Stale cursors.** A changes request whose `updatedSince` is older than the retention horizon (now minus 180 days)
  returns `410 Gone`. Clients must then discard local data for that account and do a full sync from no parameters.
  Local outbox writes must be pushed first.
- **Full syncs** (no `updatedSince` or `cursor`) are not affected. They never need tombstones older than the window
  because the server already omits them.
- **Implementation order when approved:** add the scheduled purge, then the `410` check in the changes endpoints, and
  add a test for each. Until then, tombstones are kept indefinitely, which is safe but grows storage.

## Notes for clients

- A category deleted on the server clears `categoryId` on its expenses. Clients should apply the expense feed after
  the category feed, or handle a null `categoryId` that appears on an expense.
- Creating or editing an expense with a deleted category returns 404. Clients should clear the selection and retry.
- Creating a category whose name matches a built-in returns `409` with `existingCategory`. The client adopts that
  category's `id`: it points local expenses and outbox rows at the built-in, drops the local duplicate, and does not
  retry the create. A `409` on an update is not adopted; the edit is marked failed in the outbox and exposed to the UI.
  Clients also reject built-in names locally, so this path covers only a client that has drifted from the defaults.
- Outbox failure handling, per write:
  - Network errors, `408`, `429`, and `5xx`: retry later with exponential backoff (30 s, doubling, capped at 1 h).
  - `401`, or `account_deleted`: abort the sync run and leave the outbox untouched. The client requires sign-in first.
  - Other `4xx` (for example `400`, `403`, `404`, or an unadopted `409` on update): mark the row failed and expose it
    to the UI. A failed row does not block other rows. A local edit to the same record, or an explicit retry, requeues it.
- Stale cursors: a changes request answered with `410` means the saved cursor or watermark for that feed is no longer
  valid. The client drops both for that feed and restarts it from the beginning once. A second `410` in the same pass
  is an error. The server does not emit `410` today (see Tombstone retention), so this path is covered by a client test.
- `updatedAt` is always set by the server. Clients should not send it.
- `createdAt` is nullable.
- An expense's `currency` can differ from the user's profile currency. Show it next to the amount.
- Refresh failures on `POST /api/auth/refresh` are handled by the body's `error` field:
  - `401` with `error: "account_deleted"`: the account was deleted. Wipe the local database, outbox, and tokens. Nothing
    can be synced, so the data is discarded.
  - Any other `400`, `401`, `403`, or `404` (expired, revoked, or reused refresh token): clear the tokens and require
    sign-in. Keep the local database and outbox. Unsynced expenses are kept and synced after the next sign-in.
  - Only `account_deleted` wipes local data. A deleted account's refresh rows are kept as hashed, detached records for
    about 37 days so the signal can be returned. After that, the client gets a generic `401` and treats it as an
    ordinary expiry.
- Sign-out (`SessionController.signOut`) first attempts a sync. If unsynced writes remain, it returns the count and
  changes nothing, so the UI can warn the user. The caller then confirms and calls again with `discardUnsynced`.
  Confirming wipes the local database, outbox, and tokens. Signing in as a different user runs the same flow first.
  Signing in as the same user keeps local data.


## Verification

`mobile/shared/src/jvmTest/kotlin/com/expensetracker/mobile/e2e/SyncE2ETest.kt` runs this contract against a live local
backend: register (OTP from the local profile), login, offline writes, sync, edit, delete, a second device, and refresh
rotation with reuse detection. It asserts exact minor-unit amounts across INR, JPY, and KWD. Both tests skip when the
backend is not reachable at `EXPENSEFLOW_API_URL` (default `http://localhost:8080`). Run steps are in the README
"Mobile App" section. The second-user test covers logout: a second account registered on the same device sees none of
the first account's data, locally or on the server.

## Example session

```
GET  /api/v1/categories/changes?limit=500           -> 200, built-in and own categories
POST /api/v1/expenses  (clientId=A)                 -> 201, expense 42
POST /api/v1/expenses  (clientId=A, retry)          -> 200, same expense 42
PUT  /api/v1/expenses/42                            -> 200, updatedAt advanced
DELETE /api/v1/expenses/42                          -> 204, tombstone
DELETE /api/v1/expenses/42                          -> 204, no change
PUT  /api/v1/expenses/42                            -> 404, tombstone
GET  /api/v1/expenses/changes?updatedSince=...      -> 200, includes item 42 with deletedAt
```
