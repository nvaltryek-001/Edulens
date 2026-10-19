# Database Design and CRUD

PostgreSQL is the production source of truth. Prisma schema and migrations are version-controlled. Inspect existing models before adding equivalents.

## Entities
User, RoleAssignment, Session, Invitation, Department, Program, AcademicYear, Semester, Section, Group/Batch, Subject, Student, StudentAcademicEnrollment, SubjectEnrollment, TeacherAssignment, Guardian, GuardianStudentLink, TimetableEntry, EnrollmentCampaign, EnrollmentInvitation, FaceProfile, FaceProfileVersion, FaceSampleMetadata, AttendanceSession, EvidencePhoto, AIProcessingJob, RecognitionEvidence, AttendanceResult, AttendanceCorrection, AttendanceDispute, Notification, ReportMetadata, AuditLog, SecurityEvent.

## Constraints
Normalize and uniquely constrain institutional email. Constrain official student identifier/USN according to college policy. Prevent duplicate student attendance per session. Validate timetable conflicts. Use idempotency keys. Require verified guardian links. Store photos in private object storage, not ordinary database columns. Never expose raw embeddings through ordinary APIs.

## CRUD acceptance
Create validates input and permission. Read supports pagination/filtering and object-level checks. Update validates state transitions and preserves audit history. Delete requires confirmation and authorization; use archive/controlled deletion when retention applies. Bulk import previews row errors and duplicates before explicit commit. Corrections record actor, timestamp, reason, before/after values and required approval.

## Migration procedure
Review schema diff → backup → validate Prisma → test migration in staging → smoke test → production migration with recovery plan. Never reset production data routinely.

Define retention/deletion for photos, profiles, attendance, exports and logs with college administrators and legal advisers.
