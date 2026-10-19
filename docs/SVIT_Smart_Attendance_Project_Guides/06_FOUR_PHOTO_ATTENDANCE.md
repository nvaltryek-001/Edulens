# Four-Photo Attendance

## Standard zones
1. Left/front; 2. Center; 3. Right/front; 4. Back/far rows. Four is the standard, not a strict maximum; permit recovery photos if coverage or quality is insufficient.

## Capture UX
Camera permission at point of use, guided framing, exposure/blur/stability/visibility checks, previews, capture timestamps, secure upload, progress/retry and session linkage. Keep evidence private.

## State machine
`SCHEDULED → OPEN → CAPTURING → PROCESSING → REVIEW → FINALIZED → LOCKED`; recovery: `PROCESSING → FAILED → PROCESSING`. Enforce transitions server-side. Use transactions, concurrency protection and idempotency.

## Review/finalization
Show eligible roster, candidates, evidence and quality explanations. Authorized manual resolution requires a reason. Confirm present → resolve exceptions → derive absence from eligible roster after review → show summary → transactionally finalize → record actor/time → lock → create report → queue guardian alerts only for official absences.

## Acceptance tests
Upload/retry, recovery photo, cross-photo deduplication, unknown never automatically absent, concurrent finalization, locked-session edit rejection, audited correction and notification idempotency.
