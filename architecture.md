# EduLens Architecture

## 1. Frontend (Android)
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Camera**: CameraX API for face data enrollment and classroom image capture
- **Local DB**: Room (SQLite) for offline-first architecture
- **Networking**: Retrofit & OkHttp

## 2. Backend (Flask API)
- **Framework**: Flask
- **Authentication**: PyJWT
- **Database**: SQLite (local) with users, students, attendance, and logs tables.
- **Role Control**: Administrator vs. Teacher roles.

## 3. AI / Machine Learning
- **Face Detection**: MediaPipe Face Detection
- **Feature Extraction**: MediaPipe Face Mesh (extracts geometric embeddings based on 478 landmarks relative to the nose tip)
- **Classification**: Scikit-Learn RandomForestClassifier (Ensemble model) trained on embeddings.
- **Flow**: Image -> OpenCV -> RGB -> Face Detection -> Cropping -> Face Mesh -> Landmarks -> Flattened Array -> Random Forest -> Identity.
