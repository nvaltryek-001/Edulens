# EduLens API Documentation

## `GET /health`
Returns the status of the server.

## `POST /api/register`
Registers a new user (admin/faculty).
- Payload: `{"username": "...", "password": "...", "role": "..."}`

## `POST /login`
Authenticates a user and returns a JWT.
- Payload: `{"username": "...", "password": "..."}`

## `POST /api/enroll`
Enrolls a student by uploading their face images.

## `POST /api/train`
Triggers the model training on the backend.

## `POST /api/recognize`
Accepts an image and returns recognized students.
