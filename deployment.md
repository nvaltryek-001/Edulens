# EduLens Deployment Guide

## 1. Local Development
1. `cd backend`
2. `pip install -r requirements.txt`
3. `python app.py`
4. The server runs on `http://0.0.0.0:5000`
5. Open Android Studio and compile the `app/` folder. Use `10.0.2.2` if running in the emulator.

## 2. Docker Deployment (Optional)
1. Ensure Docker and Docker Compose are installed.
2. In the root directory, run:
   `docker-compose up -d --build`
3. This spins up the Flask backend on port 5000 and mounts local volumes for database persistence and dataset storage.

## 3. Database
The system uses SQLite (`backend/edulens.db`) by default.
To migrate to Supabase in the future, add the Supabase URL and Keys to the `.env` file and update `database.py` to use `supabase-py`.
