# EduLens API Documentation

## Authentication
`POST /login`
- Body: `{"username": "admin", "password": "password"}`
- Response: `{"token": "jwt_token_here", "role": "administrator"}`

`POST /register`
- Body: `{"username": "admin", "password": "password", "role": "administrator"}`

## Student Management
`POST /api/students` (Auth Required)
- Body: `{"student_id": "ST001", "name": "John Doe", "usn": "1SV23CS001", "department": "CS", "semester": "3", "section": "A", "email": "j@test.com", "phone_number": "1234567890"}`

`POST /api/enroll`
- Body (Form Data): `studentId`, `images` (List of files)

## AI Recognition
`POST /api/train`
- Triggers model retraining.

`POST /api/recognize`
- Body (Form Data): `image` (Classroom photo)
- Response: List of recognized students.

## Attendance
`POST /api/sync`
- Body: `{"attendanceRecords": [...]}`

`GET /api/reports`
- Returns all attendance logs.
