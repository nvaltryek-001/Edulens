# Testing, Security and Deployment

## Quality gates
Inspect repository scripts first. Run applicable dependency install using lockfile, lint, typecheck, Next.js build, NestJS build/tests, Prisma validation/migration checks, Python tests/service startup, API integration tests, browser E2E, Docker validation, dependency/secret scanning. Capture exact commands, exit codes and counts. Never weaken/delete tests to pass.

## Security
Server-side RBAC and object authorization; department isolation; validation; upload type/size checks; private object storage; strong authentication and revocation; rate limits; audit; redacted logs; least-privilege database; secrets manager; backup/restore drills; retention/deletion. Review applicable Indian DPDP requirements with college/legal advisers; do not claim compliance without evidence.

## Production services
Web, NestJS API, Python AI service/worker, PostgreSQL, Redis/queue, private S3-compatible storage, approved notification provider, TLS, health checks, logs, monitoring and alerts. Verify hosting capabilities, limits and pricing at deployment time.

## Release checklist
Configure secrets → provision persistent services → review/apply migrations → deploy AI worker → deploy API/web → verify HTTPS/health/CORS/cookies/uploads → synthetic-data smoke tests → backup/restore check → Android external-network test → remove default credentials → confirm no biometric data in Git/logs/public storage.

## Pilot
Use synthetic data first. Document model evaluation, limitations, manual fallback, privacy/consent approval and support procedures before real-student use.
