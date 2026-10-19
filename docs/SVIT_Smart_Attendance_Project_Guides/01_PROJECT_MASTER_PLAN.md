# SVIT Smart Attendance — Master Plan

## Mission
Build a secure, teacher-verified, AI-assisted attendance platform for Sai Vidya Institute of Technology (SVIT), Bengaluru. This guide is not proof the application is already implemented or deployed.

## Roles
Super Admin; HOD/Department Admin; Teacher; Student; Parent/Guardian. Enforce permissions server-side, including department, class, student, session, report and guardian/ward ownership.

## Required workflow
Academic setup → authorized roster import → secure student enrollment → face-profile enrollment → timetable → scheduled class → four classroom photos (recovery photos allowed) → AI candidate processing → deduplication → teacher review → official finalization and lock → report with actual photos → notification for confirmed absences.

## Non-negotiable rules
- AI results are evidence candidates, not official attendance.
- UNKNOWN, REVIEW, OTHER CLASS, LOW QUALITY and NOT DETECTED never automatically mean ABSENT.
- Derive absences from the eligible roster only after review.
- A student appearing in multiple photos counts once.
- Corrections require authorization, reason and audit history.
- No claim of perfect recognition.
- Use synthetic/test data during development; never commit real biometric data.

## 16 modules
1 Foundation; 2 Identity/sessions/RBAC; 3 Academic engine; 4 Timetable; 5 Student self-enrollment; 6 Face profile lifecycle; 7 Attendance state machine; 8 Four-photo capture; 9 AI evidence pipeline; 10 Teacher review/finalization; 11 Reports/photo evidence; 12 Guardian notifications; 13 Role portals/analytics; 14 Security/resilience; 15 Testing/pilot; 16 Production deployment.

## Five phases
1. Modules 1–3: foundation, identity, academics.
2. Modules 4–6: timetable, roster, enrollment, profiles.
3. Modules 7–10: capture, AI, review, finalization.
4. Modules 11–13: reports, notifications, portals.
5. Modules 14–16: security, testing, deployment.

## Acceptance workflow
HOD setup → roster import → enrollment → timetable → teacher session → four photos → AI processing → review → finalization → photo report → guardian notification for official absence.

Track each feature as Not started / In progress / Implemented / Build-tested / Integration-tested / Acceptance-tested. Report actual commands, exit codes, test counts, limitations and remaining dependencies. Never claim production readiness from a successful build alone.
