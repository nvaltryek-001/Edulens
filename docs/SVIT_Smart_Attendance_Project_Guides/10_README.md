# SVIT Smart Attendance Platform

**Status:** Project guide pack only. This README does not assert that the application has already been implemented, deployed or accepted.

## Stack
Next.js/React/TypeScript; NestJS REST API; PostgreSQL/Prisma; Python/FastAPI AI service; Redis/background queue; private S3-compatible evidence storage; Docker Compose; responsive PWA and validated Android installable experience.

## Roles
Super Admin, HOD/Department Admin, Teacher, Student, Parent/Guardian.

## Workflow
Academic setup → roster import → secure enrollment → timetable → scheduled session → four classroom photos → AI candidates → teacher review → official finalization → actual-photo report → guardian notification for confirmed absence.

## Repository audit
Target repository specified by owner: `N:\svit-smart-attendance\svit-smart-attendance`.
Before editing: inspect package manifests/lockfiles, Prisma schema/migrations, frontend routes, NestJS modules, Python AI/model/license, Docker and environment configuration. Run baseline tests, create a 16-module gap checklist, back up risky changes and preserve working code. Do not create a competing repo if the path is unavailable.

## Local setup (after audit)
Install supported Node and repository package manager plus compatible Python. Copy example env files to ignored local env files. Start PostgreSQL/Redis/object-storage dev services with existing Compose configuration. Install dependencies from lockfile, apply reviewed Prisma migrations, then start services using scripts actually found in package manifests. Do not guess command names.

## Configuration
Document database URL, auth/session secrets, web/API URLs, Redis, private object storage, AI model/version, allowed origins, notification provider and retention settings. Never commit secrets or put service-role credentials in browser/mobile code.

## CRUD
Student create/read/update/archive/delete operations must be validated, authorized and audited. Attendance corrections require a reason and preserve history. Bulk import previews duplicates/errors before explicit commit. Production deletion follows approved retention rules.

## Verification
Do not claim production readiness until synthetic-data end-to-end flow passes: HOD setup → roster → enrollment → timetable → four photos → AI → review → finalize → actual-photo report → guardian notification. Record exact build/test commands and results.

## Android
Validate PWA first, then select and test packaging. Build/sign/install and verify a release APK before providing its artifact path. Keep secrets out of APKs.

## Privacy
Use synthetic data during development. Obtain appropriate institutional authorization and privacy review before real face data. Keep photos and embeddings private. Review applicable Indian DPDP requirements with institutional administrators/legal advisers.

## Guide files
`01_PROJECT_MASTER_PLAN.md`, `02_ARCHITECTURE.md`, `03_DATABASE_DESIGN.md`, `04_AUTHENTICATION_RBAC.md`, `05_FACE_ENROLLMENT_AI.md`, `06_FOUR_PHOTO_ATTENDANCE.md`, `07_REPORTS_NOTIFICATIONS.md`, `08_UI_UX_DESIGN.md`, `09_TESTING_SECURITY_DEPLOYMENT.md`, `10_README.md`.
