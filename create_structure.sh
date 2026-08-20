#!/bin/bash
mkdir -p dataset/student_001 dataset/student_002 dataset/student_003
touch dataset/student_001/image_01.jpg dataset/student_001/image_02.jpg dataset/student_001/image_50.jpg

mkdir -p models
touch models/model.pkl models/embeddings.pkl models/labels.pkl models/metadata.pkl

mkdir -p logs
touch logs/app.log logs/training.log logs/attendance.log logs/synchronization.log

mkdir -p docs
touch docs/installation.md docs/deployment.md docs/architecture.md docs/troubleshooting.md

mkdir -p scripts
touch scripts/backup.py scripts/cleanup.py scripts/initialize_database.py scripts/synchronize.py

mkdir -p backend/config backend/database/migrations backend/api backend/models backend/services backend/utilities
touch backend/config/config.py backend/config/settings.py
if [ -f backend/firebase-service-account.json ]; then
    mv backend/firebase-service-account.json backend/config/firebase-service-account.json
fi
touch backend/database/database.db backend/database/schema.sql
touch backend/api/attendance_routes.py backend/api/authentication_routes.py backend/api/enrollment_routes.py backend/api/report_routes.py backend/api/synchronization_routes.py
touch backend/models/student_model.py backend/models/attendance_model.py backend/models/log_model.py
touch backend/services/attendance_service.py backend/services/recognition_service.py backend/services/notification_service.py backend/services/storage_service.py backend/services/report_service.py backend/services/synchronization_service.py
touch backend/utilities/image_utils.py backend/utilities/logger.py backend/utilities/validator.py backend/utilities/security.py

mkdir -p output/reports/daily output/reports/weekly output/reports/monthly output/reports/yearly
mkdir -p output/exports/csv output/exports/excel output/exports/pdf

mkdir -p app/src/main/java/com/example/edulens/api
mkdir -p app/src/main/java/com/example/edulens/camera
mkdir -p app/src/main/java/com/example/edulens/authentication
mkdir -p app/src/main/java/com/example/edulens/data
mkdir -p app/src/main/java/com/example/edulens/navigation
mkdir -p app/src/main/java/com/example/edulens/notifications
mkdir -p app/src/main/java/com/example/edulens/synchronization
mkdir -p app/src/main/java/com/example/edulens/ui/screens
mkdir -p app/src/main/java/com/example/edulens/ui/theme
touch app/src/main/java/com/example/edulens/camera/CameraManager.kt app/src/main/java/com/example/edulens/camera/CameraPreview.kt app/src/main/java/com/example/edulens/camera/ImageAnalyzer.kt
touch app/src/main/java/com/example/edulens/authentication/AuthRepository.kt app/src/main/java/com/example/edulens/authentication/LoginViewModel.kt app/src/main/java/com/example/edulens/authentication/TokenManager.kt
touch app/src/main/java/com/example/edulens/navigation/NavigationGraph.kt app/src/main/java/com/example/edulens/navigation/Screen.kt
touch app/src/main/java/com/example/edulens/notifications/FirebaseMessagingService.kt
touch app/src/main/java/com/example/edulens/synchronization/SyncManager.kt app/src/main/java/com/example/edulens/synchronization/WorkerManager.kt
touch app/src/main/java/com/example/edulens/ui/screens/ProfileScreen.kt app/src/main/java/com/example/edulens/ui/screens/ReportScreen.kt app/src/main/java/com/example/edulens/ui/screens/SettingsScreen.kt

touch .env
echo "Creating structure done."
