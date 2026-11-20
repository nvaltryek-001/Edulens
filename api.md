# EduLens API examples

Base URL: `http://<server-ip>:5000`. Every route except `/login`, `/portal` and `/health` needs
`Authorization: Bearer <token>`. Errors return `{"success": false, "error"|"message": "..."}` with a 4xx/5xx code.

## Sign in

```http
POST /login
{"username": "prof.aparna", "password": "••••••••"}
```
```json
{"token": "eyJhbGciOi...", "role": "teacher"}
```

## Take attendance

### 1. Start a session

```http
POST /api/sessions/start
{"subject": "BCS701", "faculty": "Prof. Aparna", "department": "CSD", "semester": "7", "section": "A"}
```
```json
{"success": true, "session": {"sessionId": 15, "subject": "BCS701", "status": "ACTIVE", "...": "..."}}
```
`409` if this faculty member or this section already has an active session.

### 2. Upload a classroom photo (repeat up to 4 times)

```http
POST /api/recognize            (multipart/form-data)
image=<jpg/png>  subject=BCS701  faculty=Prof. Aparna  department=CSD  semester=7  section=A
```
```json
{
  "success": true,
  "pendingReview": true,
  "sessionId": 15,
  "rosterSize": 12,
  "candidatesSoFar": 9,
  "attendanceAdded": ["1VA23CD001", "1VA23CD002"],
  "duplicatesSkipped": [],
  "students": [{"studentId": "1VA23CD001", "name": "Aditi Rao", "confidence": 0.71, "attendanceStatus": "Detected - confirm to save"}]
}
```
Nothing is written to the register here.

### 3. Review

```http
GET /api/sessions/15/review
```
```json
{"success": true, "rosterSize": 12, "detectedCount": 9,
 "students": [{"studentId": "1VA23CD003", "name": "Chaitra S", "usn": "1VA23CD003", "detected": false, "confidence": null, "savedStatus": null}]}
```

### 4. Confirm (writes Present + Absent, locks, alerts parents)

```http
POST /api/attendance/confirm
{"sessionId": 15, "presentStudentIds": ["1VA23CD001", "1VA23CD003", "..."], "manualReason": "Chaitra seated behind a pillar"}
```
```json
{"success": true, "message": "Attendance saved: 10 present, 2 absent",
 "summary": {"total": 12, "present": 10, "absent": 2, "manualAdded": ["1VA23CD003"], "aiRejected": []}}
```
`/api/sessions/end` with only `sessionId` confirms the camera matches as they are. A second confirm returns `409`.

## Shortage and parent alerts

```http
GET /api/shortage?department=CSD&semester=7&section=A&threshold=85
```
```json
{"success": true, "threshold": 85, "count": 1,
 "students": [{"usn": "1VA23CD007", "subject": "BCS702", "present": 5, "total": 7, "percent": 71.4, "mustAttend": 7, "parentPhoneOnFile": true}]}
```

```http
POST /api/notifications/parents/shortage
{"department": "CSD", "semester": "7", "section": "A"}
```
```json
{"success": true, "sent": 5, "failed": 0, "results": [{"studentId": "1VA23CD007", "channel": "SMS", "recipient": "9845000007", "sent": true}]}
```

`GET /api/notifications/parents` returns the log; `POST /api/notifications/parents/test {"phone": "98XXXXXXXX"}` sends a test.

## Roster and accounts

```http
POST /api/students/import      (multipart, field "file" = CSV)
usn,name,department,semester,section,email,phone_number,parent_name,parent_phone,parent_email
```
```json
{"success": true, "inserted": 60, "updated": 0, "errors": []}
```

```http
POST /api/users
{"username": "1VA23CD007", "password": "••••••••", "role": "student", "studentId": "1VA23CD007"}
```

## Student portal

```http
GET /api/me/attendance
```
```json
{"success": true, "overallPercent": 73.3, "minimumPercent": 85,
 "subjects": [{"subject": "BCS701", "present": 6, "total": 8, "percent": 75.0, "canMiss": 0, "mustAttend": 6, "shortage": true}]}
```
`GET /api/me/marks`, `GET /api/me/assignments`.

## IA marks and deadlines

```http
POST /api/ia-marks
{"subject": "BCS701", "iaNo": 1, "maxMarks": 50, "marks": [{"studentId": "1VA23CD001", "marks": 42}]}

POST /api/assignments
{"subject": "BCS702", "department": "CSD", "semester": "7", "section": "A", "title": "Mini project synopsis", "deadline": "2026-11-28"}
```

## Enrollment (admin, from the Kotlin app)

```http
POST /api/enroll               (multipart)
studentId, name, usn, department, semester, section, email, phoneNumber, parentPhone, images=<3-5 photos>
```
