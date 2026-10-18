# SVIT UI/UX Design

Use the supplied SVIT logo as approved branding. Design direction: navy, blue, white, neutral surfaces, accessible contrast, responsive navigation and readable typography. Login title: “SVIT Smart Attendance”. Example placeholder: `name.23ds@saividya.ac.in`; domain validation is not identity verification.

## Role-specific portals
- Super Admin: institution, departments, roles, service health, model configuration, audit, backups.
- HOD: academic setup, roster import, students, subjects, teachers, timetable, enrollment, exceptions, reports.
- Teacher: timetable, session, camera preflight, four-photo capture, AI progress, review, correction, finalization.
- Student: profile, enrollment, timetable, attendance, notifications, disputes.
- Guardian: linked wards, ward timetable, official attendance and notification history.

All screens need loading, empty, success, validation, offline, permission-denied and server-error states. Every visible action must work or be clearly disabled with an explanation. Destructive actions require confirmation.

## Four-photo stepper
Validate session → camera check → photos 1–4 → upload → processing → review → summary → finalize.

## Android plan
Validate responsive PWA on Android first. Test camera, uploads, session handling, orientation, permissions, offline and HTTPS. Then choose and validate TWA/WebView/native packaging based on requirements. A screenshot is a visual reference, not an interactive login implementation. Do not claim an APK exists until built, installed and tested.
