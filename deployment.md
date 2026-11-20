# EduLens Deployment Guide (SVIT campus)

EduLens runs on one college-owned machine on the campus network. Faculty phones and browsers reach it over
Wi-Fi; no face data leaves campus.

## 1. Server

| Item | Pilot (one department, ~250 students) |
|---|---|
| CPU / RAM | 8 cores, 16 GB (InsightFace runs on CPU; ~1 GB per worker) |
| Disk | 256 GB SSD |
| OS | Ubuntu 22.04 with Docker and Docker Compose |
| Network | Fixed LAN IP or DNS name, e.g. `192.168.1.50` / `edulens.svit.local` |

## 2. Install

```bash
git clone https://github.com/nvaltryek-001/Edulens.git && cd Edulens
cp .env.example .env
python3 -c "import secrets; print(secrets.token_hex(32))"   # paste into JWT_SECRET in .env
docker compose up -d --build                                # first build downloads the face model
docker compose exec backend python create_first_admin.py
```

Check: open `http://<server-ip>:5000/portal` and sign in as the administrator.

Without Docker: `cd backend && pip install -r requirements.txt && python create_first_admin.py && python app.py`.

## 3. Load the college data

1. **Roster:** Portal → Students → Import roster. CSV columns: `usn, name, department, semester, section, email,
   phone_number, parent_name, parent_phone, parent_email` (example: `backend/sample_roster.csv`).
2. **Accounts:** Portal → Students → Create account for each faculty member (role Faculty), HODs and students
   (role Student, Student ID = USN).
3. **Faces:** in the Android app, Enrollment screen, 3–5 photos per student in good light, with signed consent.

## 4. Android app

In `gradle.properties` set `EDULENS_API_URL=http://<server-ip>:5000/`, then Build → Generate Signed APK in
Android Studio and install it on faculty phones. Faculty without the app can use the web portal on their phone.

## 5. Parent alerts

Set in `.env`, then `docker compose up -d` to apply:

| Setting | Use |
|---|---|
| `PARENT_SMS_PROVIDER=log` | Demo: messages are recorded and printed, not sent |
| `PARENT_SMS_PROVIDER=fast2sms` + `FAST2SMS_API_KEY` | Real SMS in India (route `q` for testing; DLT route for production) |
| `PARENT_SMS_PROVIDER=msg91` + `MSG91_AUTH_KEY`, `MSG91_FLOW_ID` | MSG91 Flow with a DLT-approved template |
| `PARENT_SMS_PROVIDER=twilio` + `TWILIO_*` | Twilio |
| `PARENT_EMAIL_ENABLED=true` + `SMTP_*` | E-mail (college Gmail with an App Password) |

Test from Portal → Parent alerts → Send a test message.

- Absence alerts go out automatically when a teacher confirms a session.
- Weekly shortage alerts: add a cron entry on the server:
  `0 9 * * 1  cd /path/to/Edulens && docker compose exec -T backend python send_shortage_alerts.py`

Commercial SMS in India requires a DLT-registered sender ID and the two message templates in
`backend/services/parent_notifier.py` to be approved before production use.

## 6. HTTPS (before college-wide rollout)

Put Nginx in front of port 5000 with a certificate for the college domain, then set
`cleartextTrafficPermitted="false"` in `app/src/main/res/xml/network_security_config.xml` and use the `https://`
URL in `EDULENS_API_URL`.

## 7. Backups

The database, embeddings and model cache live in the `edulens_data` Docker volume. Back it up nightly:

```bash
docker run --rm -v edulens_edulens_data:/data -v /backup:/backup alpine \
  tar czf /backup/edulens-$(date +%F).tgz -C /data .
```

## 8. Scaling up

For the whole college, move from SQLite to PostgreSQL and run more Gunicorn workers. SQLite with WAL mode is
fine for a pilot with tens of concurrent teachers.

## 9. Privacy (DPDP Act, 2023)

Collect written consent before enrolling a face, use embeddings only for attendance, keep enrollment photos in
`backend/dataset` private to the server, and delete a student's photos and embeddings when they leave.
