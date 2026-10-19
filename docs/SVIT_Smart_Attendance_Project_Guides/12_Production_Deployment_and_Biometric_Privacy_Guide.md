# Guide 12: EduLens Production Deployment and Biometric Privacy

**Project:** EduLens — SVIT Smart Attendance App  
**Document type:** Production architecture, security, migration and release guide  
**Target cloud region:** AWS Mumbai (`ap-south-1`)  
**Current Git checkpoint:** `6a6ecad` — Harden EduLens backend and prepare app integration  
**Status:** Implementation and verification required; this document is not a production approval.

---

## 1. Purpose

This guide defines the work required to evolve EduLens from a development project into a secure, testable and maintainable college attendance system.

The application combines an Android client, a Python Flask API, a database, face recognition, attendance workflows and reporting. It may process student photographs, face embeddings, identity information and attendance records. These data require appropriate access controls, security, retention and institutional oversight.

The production architecture must not be considered complete merely because the Flask application imports or an APK builds successfully.

## 2. Current project checkpoint

The following items were verified at the recorded checkpoint:

- Git branch: `feature/svit-complete-app`.
- Commit: `6a6ecad`.
- GitHub synchronization: zero commits ahead and zero behind at the last recorded check.
- Python 3.11 dependency check: passed in `backend/.venv311-prodtest`.
- Required Python imports: passed.
- Flask application import: passed.
- Flask route registration: 19 routes were reported.
- InsightFace `buffalo_l` model initialization: successful using CPU execution.
- Firebase Admin initialization: warned that `backend/config/firebase-service-account.json` was missing.

These results verify selected setup operations only. They do not establish that all routes work, Firebase notifications function, face matching is accurate, or the application is production-ready.

The `/health` test, API authorization tests, database integration tests and full Android workflow still require execution and evidence.

## 3. Target architecture

The proposed production architecture is:

1. **Android application:** Kotlin and Jetpack Compose.
2. **API:** Flask served through a production WSGI server, behind HTTPS.
3. **Compute:** Managed or appropriately secured cloud compute in AWS Mumbai (`ap-south-1`).
4. **Relational database:** Amazon RDS for PostgreSQL, with private network access, encryption and tested backups.
5. **Biometric object storage:** Private Amazon S3 bucket with Block Public Access enabled and encryption configured.
6. **Secrets:** Managed secrets storage, with separate credentials for different services.
7. **Administration:** Restricted administrator access, audited privileged actions and no public default administrator account.
8. **Monitoring:** Application errors, authentication events, database health, storage failures and security-relevant events without unnecessary biometric payloads.

Conceptual request flow:

Android app -> HTTPS API -> Authentication and authorization -> PostgreSQL

The API accesses private object storage only when the approved workflow requires it. The database should store metadata and references to objects rather than unnecessary copies of image files.

This is a target design. Cloud resources have not been provisioned or verified by this guide.

## 4. SQLite to PostgreSQL migration

The existing backend uses SQLite. Changing a cloud database setting alone will not make it PostgreSQL-compatible.

### Required work

1. Inspect `backend/database.py` and every database query in `backend/app.py` and related modules.
2. Identify SQL-specific behavior: parameter placeholders, schema creation, auto-increment IDs, transactions, date handling, schema inspection and exception handling.
3. Design a PostgreSQL schema preserving student, user, attendance, session and audit-log relationships.
4. Add appropriate primary keys, foreign keys, unique constraints, indexes and transaction boundaries.
5. Introduce an explicit database configuration using environment variables or managed secret references.
6. Keep credentials out of source code and Git.
7. Create a migration and validation procedure for existing records.
8. Test migration using a disposable copy of synthetic or appropriately protected test data.
9. Compare row counts, identifiers, attendance totals, session states and representative report results.
10. Test rollback and recovery before any production cutover.

Do not replace the existing database implementation until the PostgreSQL adapter and regression tests are ready.

## 5. Private biometric storage

### Storage rules

- Never make a biometric S3 bucket or object publicly readable.
- Enable S3 Block Public Access and encryption.
- Use narrowly scoped service permissions.
- Separate development, testing and production storage.
- Avoid exposing raw object keys or permanent public URLs to Android clients.
- Validate uploaded file types, image decoding, dimensions and size.
- Generate safe object identifiers rather than trusting client-supplied paths.
- Apply appropriate access logging and monitoring.
- Prevent biometric images and embeddings from entering application logs, crash reports or analytics unintentionally.

### Face embeddings

The project uses InsightFace and is expected to produce 512-dimensional face embeddings. Verify the actual embedding format, normalization, matching threshold and model version from the implementation and tests.

Embeddings are sensitive biometric data; they must not be treated as anonymous simply because they are numerical vectors.

Document:

- The purpose of creating each embedding.
- Which service and role may access it.
- Model and embedding version.
- Re-enrollment and replacement procedures.
- Retention and deletion requirements.
- Backup expiration and deletion behavior.
- How identity matching is evaluated and errors are handled.

Prefer storing embeddings in a protected, versioned data store when justified by the design. Do not store executable Python pickle files in an untrusted or publicly writable location, and never deserialize untrusted pickle content.

## 6. Authentication and authorization

Before deployment, verify:

- Passwords use a suitable password-hashing algorithm.
- JWT signing secrets are strong, private and supplied through secure configuration.
- Production startup fails safely if required secrets are absent.
- Tokens have suitable expiry and validation.
- Each protected endpoint verifies identity and role.
- Students can access only their permitted records.
- Faculty can access only authorized classes and attendance sessions.
- Administrator operations are restricted and audited.
- Public registration cannot grant privileged roles.
- Login failures and malformed tokens return safe error responses.
- Rate limits and monitoring are appropriate for authentication endpoints.
- CORS, HTTPS, request size limits and error messages are configured for production.

The existing hardening changes and `backend/create_first_admin.py` must be reviewed and tested before production provisioning. Never commit a real administrator password or service credential.

## 7. Indian data-protection and institutional approval

The Digital Personal Data Protection Act, 2023 and the Digital Personal Data Protection Rules, 2025 were notified on 13 November 2025. Their applicability and commencement provisions must be checked against the deployment date and the college's specific processing arrangements.

Before real student biometric data is collected, the institution should obtain an appropriate legal and administrative review covering:

- The purpose and necessity of facial recognition for attendance.
- Applicable lawful basis and notice requirements.
- Consent requirements, where applicable.
- A suitable alternative process for students who cannot or do not use facial recognition.
- Data minimization and purpose limitation.
- Retention, correction, deletion and grievance handling.
- Security safeguards and incident response.
- Vendor, cloud-provider and data-processing responsibilities.
- Rules applicable to students who are children, if relevant.
- Approval for any use beyond the original attendance purpose.

This guide is not legal advice and does not determine that a particular consent or legal basis is sufficient. Obtain institution-specific guidance before the pilot.

## 8. Android production API configuration

The Android API client previously used `http://127.0.0.1:5000/`.

That address points to the Android device itself when used on a physical phone. It is not the deployed cloud API.

Before release:

1. Identify every hard-coded development URL.
2. Configure a controlled production base URL using HTTPS.
3. Ensure login, enrollment, recognition, attendance, synchronization and reports use the correct API.
4. Avoid sending credentials or biometric files over cleartext HTTP.
5. Configure appropriate network security settings.
6. Verify TLS certificate validation and safe error handling.
7. Ensure production secrets are not embedded in the APK.
8. Build a fresh release APK.
9. Install it on a physical test phone.
10. Test the complete approved workflow against the deployed staging environment.

## 9. API and end-to-end testing

Use synthetic students and appropriate test images. Do not use real student biometric information in development tests.

Required tests:

- Health endpoint and startup configuration.
- Login success, failure and invalid-token behavior.
- Student enrollment and duplicate-identity handling.
- Image upload validation and size limits.
- Face detection and identity matching.
- No-face and multiple-face cases.
- Low-confidence and ambiguous matches.
- Attendance session creation and closure.
- The intended four-photo attendance workflow.
- Faculty verification and finalization.
- Duplicate attendance prevention.
- Student attendance history and report accuracy.
- Weekly summaries and low-attendance calculations.
- Notification behavior and failure handling.
- Unauthorized access to another student's records.
- Database outages and object-storage failures.
- Concurrent requests and transaction integrity.
- Data deletion, retention and backup recovery.

Record expected results, actual results, test data type, timestamp and pass/fail status. A successful route import is not an API test.

## 10. Deployment and operations

Use a staging environment before production.

Production checklist:

- [ ] AWS account and Mumbai region configuration approved.
- [ ] Private networking and restricted ingress configured.
- [ ] Production WSGI server and process configuration tested.
- [ ] HTTPS and certificate renewal configured.
- [ ] PostgreSQL provisioned with encryption and backups.
- [ ] S3 private bucket and encryption configured.
- [ ] IAM permissions scoped to the minimum required access.
- [ ] Secrets stored outside source control.
- [ ] Database migrations and rollback procedure tested.
- [ ] Health checks, monitoring and alerts configured.
- [ ] Backup restoration tested.
- [ ] Incident response and credential rotation documented.
- [ ] Retention and deletion behavior verified.
- [ ] College approval obtained for the pilot.

Do not assume Docker or cloud deployment configuration is ready until the actual files, image build and startup command have been checked. Do not deploy the current unverified configuration with real student data.

## 11. Release acceptance criteria

EduLens may be considered for an institution-approved pilot only after:

1. PostgreSQL migration tests pass.
2. Protected storage access tests pass.
3. Authentication and authorization tests pass.
4. Attendance records and reports are consistent.
5. Face matching is evaluated on representative, appropriately authorized test data.
6. Failure and recovery scenarios are tested.
7. The physical-device Android workflow passes.
8. HTTPS and production secrets are verified.
9. Backup restoration and deletion procedures are demonstrated.
10. Institutional approval and applicable privacy requirements are documented.

A successful APK build alone does not satisfy these criteria.

## 12. Implementation order

Recommended order:

1. Finish the Flask health test.
2. Audit all database queries and current image/embedding storage.
3. Test the existing API and role-based access controls.
4. Implement the PostgreSQL-compatible database layer.
5. Implement private biometric object storage.
6. Add and test retention, deletion and audit controls.
7. Configure secure staging infrastructure.
8. Test all Android and backend flows.
9. Deploy to the approved production environment.
10. Rebuild and verify the release APK.
11. Obtain institutional approval before a real-data pilot.

## Change log

- **Guide 12:** Added the production architecture, database migration, biometric storage, privacy, security, deployment and release acceptance plan.
- **Verification boundary:** Existing import and dependency checks are recorded as completed; unverified implementation and production work remain pending.
