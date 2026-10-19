# APK-First Delivery Rules

## Product decision
The primary deliverable for college users is an installable Android APK. Do not replace the requested product with a website-only solution.

## Supporting services
The app may require a hosted NestJS API, PostgreSQL database, private object storage, Redis/background worker and Python AI service. These are infrastructure supporting the APK, not a substitute for it.

## Mobile architecture
During repository audit, determine whether a native Android application already exists. Preserve useful existing code. If no viable native client exists, choose and document an Android approach compatible with the required camera workflow and authentication (for example, a native Kotlin client or a validated Android wrapper). Do not assume the web app can be packaged unchanged.

## APK release acceptance
- Build a debug APK for iterative testing.
- Test installation and launch on a real Android phone.
- Verify HTTPS API connectivity, login/logout, role-based access, student CRUD, camera permission, four-photo upload, review/finalization and reports.
- Keep secrets out of the APK.
- Configure a proper release signing key securely; never ship a debug-signed production APK.
- Build and install the release APK and report the exact artifact path only after the artifact exists.
- Document backend URL/environment strategy, server prerequisites, versioning, upgrade and data-retention considerations.

## Important limitation
Creating guide files does not implement the app or deploy services. Do not report the project as complete until code, infrastructure and acceptance tests are actually verified.
