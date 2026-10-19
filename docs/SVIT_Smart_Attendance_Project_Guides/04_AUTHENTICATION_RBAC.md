# Authentication, College Email and RBAC

The intended domain is `@saividya.ac.in`; example: `nikeshn.23ds@saividya.ac.in`. Domain matching alone does not prove student identity. Require an authorized roster match, invitation, approved verification or institutional SSO.

## Lifecycle
Authorized roster/account → expiring single-use identity-bound invitation → identity verification → password setup/approved SSO → applicable notice acceptance → least-privilege activation → revocable session. Recovery tokens must expire, be single-use and stored hashed.

## Permissions
Super Admin: platform governance. HOD: assigned department only. Teacher: assigned classes and attendance. Student: own profile, timetable, attendance and disputes. Guardian: verified linked wards only. Never trust client-supplied roles; enforce department and object-level authorization on every request.

## Controls
Strong password hashing; secure HttpOnly SameSite cookies where applicable; CSRF protection for cookie sessions; rate limits; login throttling; session expiry/revocation; generic recovery errors; audit sensitive changes; redact secrets from logs; no default production credentials; no secrets in Git or mobile bundles; explicit production CORS.

## Tests
Login success/failure, disabled user, expired/revoked session, role escalation, cross-department access, student-to-student access, unlinked guardian access, expired/reused invitation and rate limiting.
