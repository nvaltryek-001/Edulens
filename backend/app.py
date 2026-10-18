import os
import json

from flask import Flask, request, jsonify
from flask_cors import CORS

from werkzeug.utils import secure_filename
from werkzeug.security import generate_password_hash, check_password_hash

from ml_core import EduLensMLCore
from database import get_db_connection, init_db
from auth import generate_token, token_required
from services.notification_service import NotificationService


# ============================================================
# APP INITIALIZATION
# ============================================================

app = Flask(__name__)

CORS(app)

UPLOAD_FOLDER = 'uploads'
DATASET_FOLDER = 'dataset'

os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(DATASET_FOLDER, exist_ok=True)

app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['DATASET_FOLDER'] = DATASET_FOLDER

init_db()

ml_core = EduLensMLCore()

notification_service = NotificationService()


# ============================================================
# LOGIN
# ============================================================

@app.route('/login', methods=['POST'])
def login():

    data = request.json or {}

    if (
        not data
        or not data.get('username')
        or not data.get('password')
    ):
        return jsonify({
            'message': 'Could not verify'
        }), 401

    conn = get_db_connection()

    try:

        user = conn.execute(
            '''
            SELECT *
            FROM users
            WHERE username = ?
            ''',
            (data['username'],)
        ).fetchone()

    finally:

        conn.close()

    if not user:
        return jsonify({
            'message': 'Invalid username or password'
        }), 401

    if check_password_hash(
        user['password_hash'],
        data['password']
    ):

        token = generate_token(
            user['username'],
            user['role']
        )

        return jsonify({
            'token': token,
            'role': user['role']
        })

    return jsonify({
        'message': 'Could not verify'
    }), 401


# ============================================================
# USER REGISTRATION
# ============================================================

@app.route('/register', methods=['POST'])
def register():
    # Public registration is disabled. Accounts must be provisioned
    # through a trusted administrator-controlled process.
    return jsonify({
        'success': False,
        'message': 'Public registration is disabled. Contact your administrator.'
    }), 403

# ============================================================
# A6 — USER PROFILE
# ============================================================

@app.route('/api/profile', methods=['GET'])
@token_required
def get_profile(current_user):

    conn = get_db_connection()

    try:

        username = current_user['username']

        user = conn.execute(
            '''
            SELECT
                user_id,
                username,
                role
            FROM users
            WHERE username = ?
            ''',
            (username,)
        ).fetchone()

        if not user:

            return jsonify({
                'success': False,
                'error': 'User profile not found'
            }), 404

        return jsonify({
            'success': True,
            'profile': {
                'userId': str(user['user_id']),
                'username': user['username'],
                'role': user['role'],
                'status': 'Active'
            }
        })

    except Exception as e:

        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

    finally:

        conn.close()


# ============================================================
# ADD STUDENT
# ============================================================

@app.route('/api/students', methods=['POST'])
@token_required
def add_student(current_user):

    if current_user['role'] != 'administrator':

        return jsonify({
            'message': 'Unauthorized'
        }), 403

    data = request.json or {}

    conn = get_db_connection()

    try:

        conn.execute(
            '''
            INSERT INTO students
            (
                student_id,
                name,
                usn,
                department,
                semester,
                section,
                email,
                phone_number
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ''',
            (
                data['student_id'],
                data['name'],
                data['usn'],
                data['department'],
                data['semester'],
                data['section'],
                data['email'],
                data['phone_number']
            )
        )

        conn.commit()

    except Exception as e:

        return jsonify({
            'success': False,
            'error': str(e)
        }), 400

    finally:

        conn.close()

    return jsonify({
        'success': True,
        'message': 'Student added'
    })


# ============================================================
# REAL FACE ENROLLMENT
# ============================================================

@app.route('/api/enroll', methods=['POST'])
@token_required
def enroll_student(current_user):

    if current_user['role'] != 'administrator':

        return jsonify({
            'success': False,
            'error': 'Unauthorized'
        }), 403

    student_id = request.form.get(
        'studentId',
        ''
    ).strip()

    name = request.form.get(
        'name',
        ''
    ).strip()

    usn = request.form.get(
        'usn',
        ''
    ).strip()

    department = request.form.get(
        'department',
        ''
    ).strip()

    semester = request.form.get(
        'semester',
        ''
    ).strip()

    section = request.form.get(
        'section',
        ''
    ).strip()

    email = request.form.get(
        'email',
        ''
    ).strip()

    phone_number = request.form.get(
        'phoneNumber',
        ''
    ).strip()

    if not student_id:

        return jsonify({
            'success': False,
            'error': 'Missing studentId'
        }), 400

    if not name:

        return jsonify({
            'success': False,
            'error': 'Missing student name'
        }), 400

    images = request.files.getlist('images')

    if not images:

        return jsonify({
            'success': False,
            'error': 'No face images provided'
        }), 400

    if len(images) < 3:

        return jsonify({
            'success': False,
            'error': (
                'At least 3 face images are required. '
                f'Received {len(images)}.'
            )
        }), 400

    student_dir = os.path.join(
        app.config['DATASET_FOLDER'],
        secure_filename(student_id)
    )

    os.makedirs(
        student_dir,
        exist_ok=True
    )

    saved_paths = []

    try:

        for index, image in enumerate(
            images,
            start=1
        ):

            if (
                image is None
                or not image.filename
            ):
                continue

            extension = os.path.splitext(
                secure_filename(
                    image.filename
                )
            )[1].lower()

            if extension not in [
                '.jpg',
                '.jpeg',
                '.png',
                '.webp'
            ]:
                extension = '.jpg'

            filename = (
                f'enroll_{index}{extension}'
            )

            image_path = os.path.join(
                student_dir,
                filename
            )

            image.save(image_path)

            if (
                os.path.exists(image_path)
                and os.path.getsize(image_path) > 0
            ):

                saved_paths.append(
                    image_path
                )

    except Exception as e:

        return jsonify({
            'success': False,
            'error': (
                'Failed to save enrollment images: '
                f'{str(e)}'
            )
        }), 500

    if len(saved_paths) < 3:

        return jsonify({
            'success': False,
            'error': (
                'Not enough valid images were saved. '
                f'Valid images: {len(saved_paths)}'
            )
        }), 400

    try:

        enrollment_success, embedding_count = (
            ml_core.enroll_student(
                student_id=student_id,
                image_paths=saved_paths
            )
        )

    except Exception as e:

        print(
            'Enrollment embedding error:',
            e
        )

        return jsonify({
            'success': False,
            'error': (
                'Face embedding generation failed: '
                f'{str(e)}'
            )
        }), 500

    if not enrollment_success:

        return jsonify({
            'success': False,
            'error': (
                'No valid face embeddings could be generated. '
                'Make sure each image contains exactly one '
                'clearly visible face.'
            )
        }), 400

    conn = get_db_connection()

    try:

        existing_student = conn.execute(
            '''
            SELECT student_id
            FROM students
            WHERE student_id = ?
            ''',
            (student_id,)
        ).fetchone()

        if existing_student:

            conn.execute(
                '''
                UPDATE students
                SET
                    name = ?,
                    usn = ?,
                    department = ?,
                    semester = ?,
                    section = ?,
                    email = ?,
                    phone_number = ?
                WHERE student_id = ?
                ''',
                (
                    name,
                    usn,
                    department,
                    semester,
                    section,
                    email,
                    phone_number,
                    student_id
                )
            )

        else:

            conn.execute(
                '''
                INSERT INTO students
                (
                    student_id,
                    name,
                    usn,
                    department,
                    semester,
                    section,
                    email,
                    phone_number
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ''',
                (
                    student_id,
                    name,
                    usn,
                    department,
                    semester,
                    section,
                    email,
                    phone_number
                )
            )

        conn.commit()

    except Exception as e:

        conn.rollback()

        try:
            ml_core.clear_student_embeddings(
                student_id
            )
        except Exception:
            pass

        return jsonify({
            'success': False,
            'error': (
                'Student database update failed: '
                f'{str(e)}'
            )
        }), 500

    finally:

        conn.close()

    return jsonify({
        'success': True,
        'message': 'Student enrolled successfully',
        'studentId': student_id,
        'name': name,
        'embeddingCount': embedding_count
    }), 200


# ============================================================
# TRAIN
# ============================================================

@app.route('/api/train', methods=['POST'])
@token_required
def train_model(current_user):

    if current_user['role'] != 'administrator':

        return jsonify({
            'success': False,
            'error': 'Unauthorized'
        }), 403

    try:

        trained, num_classes = ml_core.train(
            app.config['DATASET_FOLDER']
        )

        if trained:

            return jsonify({
                'success': True,
                'message': (
                    f'Model trained on '
                    f'{num_classes} classes.'
                )
            })

        return jsonify({
            'success': False,
            'message': 'Training failed or no data.'
        })

    except Exception as e:

        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


# ============================================================
# FACE RECOGNITION + ATTENDANCE
# ============================================================

@app.route('/api/recognize', methods=['POST'])
@token_required
def recognize_frame(current_user):

    if current_user['role'] not in [
        'administrator',
        'teacher',
        'faculty'
    ]:

        return jsonify({
            'success': False,
            'error': 'Unauthorized'
        }), 403

    if 'image' not in request.files:

        return jsonify({
            'success': False,
            'error': 'No image file provided'
        }), 400

    file = request.files['image']

    if file.filename == '':

        return jsonify({
            'success': False,
            'error': 'No image selected'
        }), 400

    subject = request.form.get(
        'subject',
        ''
    ).strip()

    faculty = request.form.get(
        'faculty',
        ''
    ).strip()

    department = request.form.get(
        'department',
        ''
    ).strip()

    semester = request.form.get(
        'semester',
        ''
    ).strip()

    section = request.form.get(
        'section',
        ''
    ).strip()

    missing = []

    if not subject:
        missing.append('subject')

    if not faculty:
        missing.append('faculty')

    if not department:
        missing.append('department')

    if not semester:
        missing.append('semester')

    if not section:
        missing.append('section')

    if missing:

        return jsonify({
            'success': False,
            'error': 'Missing class information',
            'missingFields': missing
        }), 400

    filename = secure_filename(
        file.filename
    )

    filepath = os.path.join(
        app.config['UPLOAD_FOLDER'],
        filename
    )

    file.save(filepath)

    conn = None

    try:

        conn = get_db_connection()

        # ----------------------------------------------------
        # CHECK ACTIVE SESSION FIRST
        # ----------------------------------------------------

        active_session = conn.execute(
            '''
            SELECT *
            FROM attendance_sessions
            WHERE status = 'ACTIVE'
              AND subject = ?
              AND faculty = ?
              AND department = ?
              AND semester = ?
              AND section = ?
            ORDER BY session_id DESC
            LIMIT 1
            ''',
            (
                subject,
                faculty,
                department,
                semester,
                section
            )
        ).fetchone()

        if not active_session:

            return jsonify({
                'success': False,
                'students': [],
                'error':
                    'No active attendance session found for this class. '
                    'Start the attendance session first.'
            }), 400

        session_id = active_session['session_id']

        # ----------------------------------------------------
        # LOAD STUDENTS
        # ----------------------------------------------------

        rows = conn.execute(
            '''
            SELECT *
            FROM students
            '''
        ).fetchall()

        student_db = {
            row['student_id']: dict(row)
            for row in rows
        }

        # ----------------------------------------------------
        # FACE RECOGNITION
        # ----------------------------------------------------

        students = ml_core.recognize_faces(
            filepath,
            student_db
        )

        from datetime import datetime

        now = datetime.now()

        today = now.strftime(
            '%Y-%m-%d'
        )

        current_time = now.strftime(
            '%H:%M:%S'
        )

        attendance_added = []
        attendance_duplicate = []

        # ----------------------------------------------------
        # PROCESS RECOGNIZED STUDENTS
        # ----------------------------------------------------

        for student in students:

            student_id = student.get(
                'studentId'
            )

            if not student_id:
                continue

            student_info = student_db.get(
                student_id
            )

            if not student_info:
                continue

            # ------------------------------------------------
            # CLASS VALIDATION
            # ------------------------------------------------

            if (
                student_info.get('department')
                != department
                or str(
                    student_info.get('semester')
                )
                != str(semester)
                or student_info.get('section')
                != section
            ):
                continue

            # ------------------------------------------------
            # DUPLICATE CHECK FOR THIS SESSION
            # ------------------------------------------------

            existing = conn.execute(
                '''
                SELECT attendance_id
                FROM attendance
                WHERE student_id = ?
                  AND session_id = ?
                  AND status = 'Present'
                LIMIT 1
                ''',
                (
                    student_id,
                    session_id
                )
            ).fetchone()

            if existing:

                attendance_duplicate.append(
                    student_id
                )

                continue

            # ------------------------------------------------
            # INSERT ATTENDANCE
            # ------------------------------------------------

            conn.execute(
                '''
                INSERT INTO attendance
                (
                    student_id,
                    date,
                    time,
                    status,
                    subject,
                    faculty,
                    session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ''',
                (
                    student_id,
                    today,
                    current_time,
                    'Present',
                    subject,
                    faculty,
                    session_id
                )
            )

            attendance_added.append(
                student_id
            )

        conn.commit()

        return jsonify({

            'success': True,

            'students': students,

            'attendanceAdded':
                attendance_added,

            'duplicatesSkipped':
                attendance_duplicate,

            'sessionId':
                session_id,

            'class': {

                'department':
                    department,

                'semester':
                    semester,

                'section':
                    section,

                'subject':
                    subject,

                'faculty':
                    faculty
            }
        })

    except Exception as e:

        print(
            f'Recognition/Attendance Error: {e}'
        )

        if conn:

            try:
                conn.rollback()
            except Exception:
                pass

        return jsonify({
            'success': False,
            'students': [],
            'error': str(e)
        }), 500

    finally:

        if conn:

            try:
                conn.close()
            except Exception:
                pass

        if os.path.exists(filepath):

            try:
                os.remove(filepath)
            except Exception:
                pass


# ============================================================
# HEALTH
# ============================================================

@app.route('/health', methods=['GET'])
def health_check():

    return jsonify({
        'status': 'healthy',
        'message': 'EduLens Backend is running'
    })


# ============================================================
# ATTENDANCE
# ============================================================

@app.route('/api/attendance', methods=['GET'])
@token_required
def get_attendance(current_user):

    if current_user['role'] not in [
        'administrator',
        'teacher',
        'faculty'
    ]:

        return jsonify({
            'success': False,
            'error': 'Unauthorized'
        }), 403

    conn = get_db_connection()

    try:

        rows = conn.execute(
            '''
            SELECT *
            FROM attendance
            ORDER BY date DESC, time DESC
            '''
        ).fetchall()

        return jsonify({
            'success': True,
            'attendance': [
                dict(row)
                for row in rows
            ]
        })

    except Exception as e:

        return jsonify({
            'success': False,
            'error': str(e)
        })

    finally:

        conn.close()


# ============================================================
# A2 — WEEKLY ATTENDANCE SUMMARY
# ============================================================

@app.route('/api/weekly-summary', methods=['GET'])
@token_required
def get_weekly_summary(current_user):

    if current_user['role'] not in [
        'administrator',
        'teacher',
        'faculty'
    ]:

        return jsonify({
            'success': False,
            'error': 'Unauthorized'
        }), 403

    conn = get_db_connection()

    try:

        from datetime import datetime, timedelta

        department = request.args.get(
            'department',
            ''
        ).strip()

        semester = request.args.get(
            'semester',
            ''
        ).strip()

        section = request.args.get(
            'section',
            ''
        ).strip()

        subject = request.args.get(
            'subject',
            ''
        ).strip()

        result = []

        today = datetime.now().date()

        for offset in range(6, -1, -1):

            current_date = (
                today -
                timedelta(days=offset)
            )

            date_string = current_date.strftime(
                '%Y-%m-%d'
            )

            query = '''
                SELECT
                    COUNT(*) AS total,

                    SUM(
                        CASE
                            WHEN a.status = 'Present'
                            THEN 1
                            ELSE 0
                        END
                    ) AS present,

                    SUM(
                        CASE
                            WHEN a.status = 'Absent'
                            THEN 1
                            ELSE 0
                        END
                    ) AS absent

                FROM attendance a

                JOIN students s
                    ON a.student_id = s.student_id

                WHERE a.date = ?
            '''

            params = [
                date_string
            ]

            if department:

                query += '''
                    AND s.department = ?
                '''

                params.append(
                    department
                )

            if semester:

                query += '''
                    AND s.semester = ?
                '''

                params.append(
                    semester
                )

            if section:

                query += '''
                    AND s.section = ?
                '''

                params.append(
                    section
                )

            if subject:

                query += '''
                    AND a.subject = ?
                '''

                params.append(
                    subject
                )

            row = conn.execute(
                query,
                params
            ).fetchone()

            total = row['total'] or 0
            present = row['present'] or 0
            absent = row['absent'] or 0

            percentage = (

                round(
                    (
                        present /
                        total
                    ) * 100,
                    2
                )

                if total > 0
                else 0.0
            )

            result.append({

                'date':
                    date_string,

                'day':
                    current_date.strftime(
                        '%a'
                    ),

                'total':
                    total,

                'present':
                    present,

                'absent':
                    absent,

                'attendancePercentage':
                    percentage
            })

        return jsonify({

            'success':
                True,

            'filters': {

                'department':
                    department or None,

                'semester':
                    semester or None,

                'section':
                    section or None,

                'subject':
                    subject or None
            },

            'days':
                result
        })

    except Exception as e:

        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

    finally:

        conn.close()


# ============================================================
# A8 — START ATTENDANCE SESSION
# ============================================================

@app.route('/api/sessions/start', methods=['POST'])
@token_required
def start_attendance_session(current_user):

    if current_user['role'] not in [
        'administrator',
        'faculty',
        'teacher'
    ]:

        return jsonify({
            'success': False,
            'message': 'Unauthorized'
        }), 403

    data = request.json or {}

    subject = str(
        data.get('subject', '')
    ).strip()

    faculty = str(
        data.get('faculty', '')
    ).strip()

    department = str(
        data.get('department', '')
    ).strip()

    semester = str(
        data.get('semester', '')
    ).strip()

    section = str(
        data.get('section', '')
    ).strip()

    if not subject:
        return jsonify({
            'success': False,
            'message': 'Subject is required'
        }), 400

    if not faculty:
        return jsonify({
            'success': False,
            'message': 'Faculty is required'
        }), 400

    if not department:
        return jsonify({
            'success': False,
            'message': 'Department is required'
        }), 400

    if not semester:
        return jsonify({
            'success': False,
            'message': 'Semester is required'
        }), 400

    if not section:
        return jsonify({
            'success': False,
            'message': 'Section is required'
        }), 400

    conn = get_db_connection()

    try:

        active_session = conn.execute(
            '''
            SELECT *
            FROM attendance_sessions
            WHERE status = 'ACTIVE'
            ORDER BY session_id DESC
            LIMIT 1
            '''
        ).fetchone()

        if active_session:

            return jsonify({

                'success': False,

                'message':
                    'An attendance session is already active',

                'session': {

                    'sessionId':
                        active_session['session_id'],

                    'subject':
                        active_session['subject'],

                    'faculty':
                        active_session['faculty'],

                    'department':
                        active_session['department'],

                    'semester':
                        active_session['semester'],

                    'section':
                        active_session['section'],

                    'startTime':
                        active_session['start_time'],

                    'endTime':
                        active_session['end_time'],

                    'status':
                        active_session['status']
                }

            }), 409

        from datetime import datetime

        start_time = datetime.now().strftime(
            '%Y-%m-%d %H:%M:%S'
        )

        cursor = conn.execute(
            '''
            INSERT INTO attendance_sessions
            (
                subject,
                faculty,
                department,
                semester,
                section,
                start_time,
                end_time,
                status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ''',
            (
                subject,
                faculty,
                department,
                semester,
                section,
                start_time,
                None,
                'ACTIVE'
            )
        )

        conn.commit()

        session_id = cursor.lastrowid

        return jsonify({

            'success':
                True,

            'message':
                'Attendance session started successfully',

            'session': {

                'sessionId':
                    session_id,

                'subject':
                    subject,

                'faculty':
                    faculty,

                'department':
                    department,

                'semester':
                    semester,

                'section':
                    section,

                'startTime':
                    start_time,

                'endTime':
                    None,

                'status':
                    'ACTIVE'
            }

        }), 201

    except Exception as e:

        return jsonify({

            'success':
                False,

            'message':
                'Failed to start attendance session',

            'error':
                str(e)

        }), 500

    finally:

        conn.close()


# ============================================================
# A8 — END ATTENDANCE SESSION
# ============================================================

@app.route('/api/sessions/end', methods=['POST'])
@token_required
def end_attendance_session(current_user):

    if current_user['role'] not in [
        'administrator',
        'faculty',
        'teacher'
    ]:

        return jsonify({
            'success': False,
            'message': 'Unauthorized'
        }), 403

    data = request.json or {}

    session_id = data.get(
        'sessionId'
    )

    if not session_id:

        return jsonify({
            'success': False,
            'message': 'sessionId is required'
        }), 400

    conn = get_db_connection()

    try:

        session = conn.execute(
            '''
            SELECT *
            FROM attendance_sessions
            WHERE session_id = ?
            ''',
            (session_id,)
        ).fetchone()

        if not session:

            return jsonify({
                'success': False,
                'message':
                    'Attendance session not found'
            }), 404

        if session['status'] == 'ENDED':

            return jsonify({
                'success': False,
                'message':
                    'Attendance session is already ended'
            }), 409

        from datetime import datetime

        end_time = datetime.now().strftime(
            '%Y-%m-%d %H:%M:%S'
        )

        conn.execute(
            '''
            UPDATE attendance_sessions
            SET
                end_time = ?,
                status = 'ENDED'
            WHERE session_id = ?
            ''',
            (
                end_time,
                session_id
            )
        )

        conn.commit()

        updated_session = conn.execute(
            '''
            SELECT *
            FROM attendance_sessions
            WHERE session_id = ?
            ''',
            (session_id,)
        ).fetchone()

        return jsonify({

            'success':
                True,

            'message':
                'Attendance session ended successfully',

            'session': {

                'sessionId':
                    updated_session['session_id'],

                'subject':
                    updated_session['subject'],

                'faculty':
                    updated_session['faculty'],

                'department':
                    updated_session['department'],

                'semester':
                    updated_session['semester'],

                'section':
                    updated_session['section'],

                'startTime':
                    updated_session['start_time'],

                'endTime':
                    updated_session['end_time'],

                'status':
                    updated_session['status']
            }

        })

    except Exception as e:

        return jsonify({

            'success':
                False,

            'message':
                'Failed to end attendance session',

            'error':
                str(e)

        }), 500

    finally:

        conn.close()


# ============================================================
# A8 — GET ATTENDANCE SESSIONS
# ============================================================

@app.route('/api/sessions', methods=['GET'])
@token_required
def get_attendance_sessions(current_user):

    if current_user['role'] not in [
        'administrator',
        'faculty',
        'teacher'
    ]:

        return jsonify({
            'success': False,
            'message': 'Unauthorized'
        }), 403

    conn = get_db_connection()

    try:

        rows = conn.execute(
            '''
            SELECT *
            FROM attendance_sessions
            ORDER BY session_id DESC
            '''
        ).fetchall()

        sessions = []

        for row in rows:

            sessions.append({

                'sessionId':
                    row['session_id'],

                'subject':
                    row['subject'],

                'faculty':
                    row['faculty'],

                'department':
                    row['department'],

                'semester':
                    row['semester'],

                'section':
                    row['section'],

                'startTime':
                    row['start_time'],

                'endTime':
                    row['end_time'],

                'status':
                    row['status']
            })

        return jsonify({

            'success':
                True,

            'count':
                len(sessions),

            'sessions':
                sessions
        })

    except Exception as e:

        return jsonify({

            'success':
                False,

            'message':
                'Failed to load attendance sessions',

            'error':
                str(e)

        }), 500

    finally:

        conn.close()


# ============================================================
# SYNC
# ============================================================

@app.route('/api/sync', methods=['POST'])
@token_required
def sync_attendance(current_user):

    if current_user['role'] not in [
        'administrator',
        'teacher',
        'faculty'
    ]:

        return jsonify({
            'success': False,
            'error': 'Unauthorized'
        }), 403

    data = request.json or {}

    if 'attendanceRecords' not in data:

        return jsonify({
            'success': False,
            'message': 'Invalid payload'
        }), 400

    records = data[
        'attendanceRecords'
    ]

    conn = get_db_connection()

    synced_count = 0

    try:

        for record in records:

            existing = conn.execute(
                '''
                SELECT attendance_id
                FROM attendance
                WHERE student_id = ?
                  AND date = ?
                  AND time = ?
                  AND subject = ?
                LIMIT 1
                ''',
                (
                    record.get('studentId'),
                    record.get('date'),
                    record.get('time'),
                    record.get('subject')
                )
            ).fetchone()

            if existing:
                continue

            conn.execute(
                '''
                INSERT INTO attendance
                (
                    student_id,
                    date,
                    time,
                    status,
                    subject,
                    faculty,
                    session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ''',
                (
                    record.get('studentId'),
                    record.get('date'),
                    record.get('time'),
                    record.get('status'),
                    record.get('subject'),
                    record.get('faculty'),
                    record.get('sessionId')
                )
            )

            synced_count += 1

        conn.commit()

    except Exception as e:

        conn.rollback()

        return jsonify({
            'success': False,
            'message': str(e)
        }), 500

    finally:

        conn.close()

    return jsonify({

        'success':
            True,

        'message':
            f'Successfully synced {synced_count} records.'
    })


# ============================================================
# REPORTS
# ============================================================

@app.route('/api/reports', methods=['GET'])
@token_required
def get_reports(current_user):

    if current_user['role'] not in [
        'administrator',
        'teacher',
        'faculty'
    ]:

        return jsonify({
            'success': False,
            'error': 'Unauthorized'
        }), 403

    conn = get_db_connection()

    try:

        date = request.args.get(
            'date',
            ''
        ).strip()

        department = request.args.get(
            'department',
            ''
        ).strip()

        semester = request.args.get(
            'semester',
            ''
        ).strip()

        section = request.args.get(
            'section',
            ''
        ).strip()

        subject = request.args.get(
            'subject',
            ''
        ).strip()

        faculty = request.args.get(
            'faculty',
            ''
        ).strip()

        query = '''
            SELECT
                a.attendance_id,
                a.student_id,
                a.date,
                a.time,
                a.status,
                a.subject,
                a.faculty,
                a.session_id,
                s.name,
                s.usn,
                s.department,
                s.semester,
                s.section
            FROM attendance a
            JOIN students s
                ON a.student_id = s.student_id
            WHERE 1=1
        '''

        params = []

        if date:

            query += ' AND a.date = ?'
            params.append(date)

        if department:

            query += ' AND s.department = ?'
            params.append(department)

        if semester:

            query += ' AND s.semester = ?'
            params.append(semester)

        if section:

            query += ' AND s.section = ?'
            params.append(section)

        if subject:

            query += ' AND a.subject = ?'
            params.append(subject)

        if faculty:

            query += ' AND a.faculty = ?'
            params.append(faculty)

        query += '''
            ORDER BY
                a.date DESC,
                a.time DESC
        '''

        rows = conn.execute(
            query,
            params
        ).fetchall()

        reports = [
            dict(row)
            for row in rows
        ]

        total = len(reports)

        present = sum(
            1
            for row in reports
            if row['status'] == 'Present'
        )

        absent = sum(
            1
            for row in reports
            if row['status'] == 'Absent'
        )

        attendance_percentage = (

            round(
                (present / total) * 100,
                2
            )

            if total > 0
            else 0
        )

        return jsonify({

            'success':
                True,

            'filters': {

                'date':
                    date or None,

                'department':
                    department or None,

                'semester':
                    semester or None,

                'section':
                    section or None,

                'subject':
                    subject or None,

                'faculty':
                    faculty or None
            },

            'summary': {

                'totalRecords':
                    total,

                'present':
                    present,

                'absent':
                    absent,

                'attendancePercentage':
                    attendance_percentage
            },

            'reports':
                reports
        })

    except Exception as e:

        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

    finally:

        conn.close()


# ============================================================
# ANALYTICS
# ============================================================

@app.route('/api/analytics', methods=['GET'])
@token_required
def get_analytics(current_user):

    if current_user['role'] not in [
        'administrator',
        'teacher',
        'faculty'
    ]:

        return jsonify({
            'success': False,
            'error': 'Unauthorized'
        }), 403

    conn = get_db_connection()

    try:

        from datetime import datetime

        today = datetime.now().strftime(
            '%Y-%m-%d'
        )

        department = request.args.get(
            'department',
            ''
        ).strip()

        semester = request.args.get(
            'semester',
            ''
        ).strip()

        section = request.args.get(
            'section',
            ''
        ).strip()

        subject = request.args.get(
            'subject',
            ''
        ).strip()

        # ----------------------------------------------------
        # TOTAL STUDENTS
        # ----------------------------------------------------

        student_query = '''
            SELECT COUNT(*) AS total
            FROM students
            WHERE 1=1
        '''

        student_params = []

        if department:

            student_query += (
                ' AND department = ?'
            )

            student_params.append(
                department
            )

        if semester:

            student_query += (
                ' AND semester = ?'
            )

            student_params.append(
                semester
            )

        if section:

            student_query += (
                ' AND section = ?'
            )

            student_params.append(
                section
            )

        total_students = conn.execute(
            student_query,
            student_params
        ).fetchone()['total']

        # ----------------------------------------------------
        # TODAY ATTENDANCE
        # ----------------------------------------------------

        attendance_query = '''
            SELECT
                COUNT(*) AS total_records,

                SUM(
                    CASE
                        WHEN a.status = 'Present'
                        THEN 1
                        ELSE 0
                    END
                ) AS present,

                SUM(
                    CASE
                        WHEN a.status = 'Absent'
                        THEN 1
                        ELSE 0
                    END
                ) AS absent

            FROM attendance a

            JOIN students s
                ON a.student_id = s.student_id

            WHERE a.date = ?
        '''

        attendance_params = [
            today
        ]

        if department:

            attendance_query += (
                ' AND s.department = ?'
            )

            attendance_params.append(
                department
            )

        if semester:

            attendance_query += (
                ' AND s.semester = ?'
            )

            attendance_params.append(
                semester
            )

        if section:

            attendance_query += (
                ' AND s.section = ?'
            )

            attendance_params.append(
                section
            )

        if subject:

            attendance_query += (
                ' AND a.subject = ?'
            )

            attendance_params.append(
                subject
            )

        attendance = conn.execute(
            attendance_query,
            attendance_params
        ).fetchone()

        total_records = (
            attendance['total_records']
            or 0
        )

        present_today = (
            attendance['present']
            or 0
        )

        absent_today = (
            attendance['absent']
            or 0
        )

        attendance_percentage = (

            round(
                (
                    present_today /
                    total_records
                ) * 100,
                2
            )

            if total_records > 0
            else 0
        )

        # ----------------------------------------------------
        # DEPARTMENT STATISTICS
        # ----------------------------------------------------

        department_rows = conn.execute(
            '''
            SELECT
                s.department,
                COUNT(a.attendance_id) AS total,

                SUM(
                    CASE
                        WHEN a.status = 'Present'
                        THEN 1
                        ELSE 0
                    END
                ) AS present

            FROM students s

            LEFT JOIN attendance a
                ON s.student_id = a.student_id

            GROUP BY s.department

            ORDER BY s.department
            '''
        ).fetchall()

        department_stats = []

        for row in department_rows:

            total = row['total'] or 0
            present = row['present'] or 0

            percentage = (

                round(
                    (present / total) * 100,
                    2
                )

                if total > 0
                else 0
            )

            department_stats.append({

                'department':
                    row['department'],

                'totalRecords':
                    total,

                'present':
                    present,

                'attendancePercentage':
                    percentage
            })

        # ----------------------------------------------------
        # SUBJECT STATISTICS
        # ----------------------------------------------------

        subject_rows = conn.execute(
            '''
            SELECT
                a.subject,
                COUNT(*) AS total,

                SUM(
                    CASE
                        WHEN a.status = 'Present'
                        THEN 1
                        ELSE 0
                    END
                ) AS present

            FROM attendance a

            GROUP BY a.subject

            ORDER BY total DESC
            '''
        ).fetchall()

        subject_stats = []

        for row in subject_rows:

            total = row['total'] or 0
            present = row['present'] or 0

            percentage = (

                round(
                    (present / total) * 100,
                    2
                )

                if total > 0
                else 0
            )

            subject_stats.append({

                'subject':
                    row['subject'],

                'totalRecords':
                    total,

                'present':
                    present,

                'attendancePercentage':
                    percentage
            })

        # ----------------------------------------------------
        # RECENT ATTENDANCE
        # ----------------------------------------------------

        recent_rows = conn.execute(
            '''
            SELECT
                a.attendance_id,
                a.student_id,
                s.name,
                s.department,
                s.semester,
                s.section,
                a.date,
                a.time,
                a.status,
                a.subject,
                a.faculty,
                a.session_id

            FROM attendance a

            JOIN students s
                ON a.student_id = s.student_id

            ORDER BY
                a.date DESC,
                a.time DESC

            LIMIT 10
            '''
        ).fetchall()

        recent_attendance = [
            dict(row)
            for row in recent_rows
        ]

        return jsonify({

            'success':
                True,

            'date':
                today,

            'filters': {

                'department':
                    department or None,

                'semester':
                    semester or None,

                'section':
                    section or None,

                'subject':
                    subject or None
            },

            'overview': {

                'totalStudents':
                    total_students,

                'presentToday':
                    present_today,

                'absentToday':
                    absent_today,

                'attendancePercentage':
                    attendance_percentage
            },

            'departmentStats':
                department_stats,

            'subjectStats':
                subject_stats,

            'recentAttendance':
                recent_attendance
        })

    except Exception as e:

        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

    finally:

        conn.close()


# ============================================================
# A5 — LOW ATTENDANCE STUDENTS
# ============================================================

@app.route('/api/low-attendance', methods=['GET'])
@token_required
def get_low_attendance(current_user):

    if current_user['role'] not in [
        'administrator',
        'teacher',
        'faculty'
    ]:

        return jsonify({
            'success': False,
            'error': 'Unauthorized'
        }), 403

    conn = get_db_connection()

    try:

        threshold_raw = request.args.get(
            'threshold',
            '75'
        ).strip()

        department = request.args.get(
            'department',
            ''
        ).strip()

        semester = request.args.get(
            'semester',
            ''
        ).strip()

        section = request.args.get(
            'section',
            ''
        ).strip()

        try:

            threshold = float(
                threshold_raw
            )

        except ValueError:

            return jsonify({
                'success': False,
                'error': (
                    'Invalid threshold. '
                    'Use a number such as 75.'
                )
            }), 400

        threshold = max(
            0,
            min(100, threshold)
        )

        query = '''
            SELECT

                s.student_id,
                s.name,
                s.usn,
                s.department,
                s.semester,
                s.section,

                COUNT(a.attendance_id)
                    AS total_classes,

                SUM(
                    CASE
                        WHEN a.status = 'Present'
                        THEN 1
                        ELSE 0
                    END
                ) AS present_classes,

                SUM(
                    CASE
                        WHEN a.status = 'Absent'
                        THEN 1
                        ELSE 0
                    END
                ) AS absent_classes

            FROM students s

            LEFT JOIN attendance a
                ON s.student_id = a.student_id

            WHERE 1 = 1
        '''

        params = []

        if department:

            query += '''
                AND s.department = ?
            '''

            params.append(
                department
            )

        if semester:

            query += '''
                AND s.semester = ?
            '''

            params.append(
                semester
            )

        if section:

            query += '''
                AND s.section = ?
            '''

            params.append(
                section
            )

        query += '''
            GROUP BY
                s.student_id,
                s.name,
                s.usn,
                s.department,
                s.semester,
                s.section
        '''

        rows = conn.execute(
            query,
            params
        ).fetchall()

        students = []

        for row in rows:

            total_classes = (
                row['total_classes']
                or 0
            )

            present_classes = (
                row['present_classes']
                or 0
            )

            absent_classes = (
                row['absent_classes']
                or 0
            )

            if total_classes > 0:

                attendance_percentage = round(
                    (
                        present_classes /
                        total_classes
                    ) * 100,
                    2
                )

            else:

                attendance_percentage = 0.0

            if attendance_percentage <= threshold:

                students.append({

                    'studentId':
                        row['student_id'],

                    'name':
                        row['name'],

                    'usn':
                        row['usn'],

                    'department':
                        row['department'],

                    'semester':
                        row['semester'],

                    'section':
                        row['section'],

                    'totalClasses':
                        total_classes,

                    'presentClasses':
                        present_classes,

                    'absentClasses':
                        absent_classes,

                    'attendancePercentage':
                        attendance_percentage
                })

        students.sort(
            key=lambda student:
                student['attendancePercentage']
        )

        return jsonify({

            'success':
                True,

            'threshold':
                threshold,

            'count':
                len(students),

            'filters': {

                'department':
                    department or None,

                'semester':
                    semester or None,

                'section':
                    section or None
            },

            'students':
                students
        })

    except Exception as e:

        print(
            f'Low Attendance Error: {e}'
        )

        return jsonify({

            'success':
                False,

            'error':
                str(e)

        }), 500

    finally:

        conn.close()


# ============================================================
# NOTIFICATIONS
# ============================================================

@app.route('/api/notifications', methods=['POST'])
@token_required
def send_notification(current_user):

    data = request.json or {}

    token = data.get(
        'token',
        ''
    ).strip()

    title = data.get(
        'title',
        ''
    ).strip()

    body = data.get(
        'body',
        ''
    ).strip()

    notification_data = data.get(
        'data',
        {}
    )

    if not token:

        return jsonify({
            'success': False,
            'error': 'FCM token is required'
        }), 400

    if not title:

        return jsonify({
            'success': False,
            'error': 'Notification title is required'
        }), 400

    if not body:

        return jsonify({
            'success': False,
            'error': 'Notification body is required'
        }), 400

    try:

        sent = (
            notification_service
            .send_notification(
                token=token,
                title=title,
                body=body,
                data=notification_data
            )
        )

        if not sent:

            return jsonify({
                'success': False,
                'error': (
                    'Firebase notification service '
                    'is not configured or '
                    'notification failed'
                )
            }), 503

        return jsonify({
            'success': True,
            'message':
                'Notification sent successfully'
        })

    except Exception as e:

        print(
            f'Notification Error: {e}'
        )

        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


# ============================================================
# START SERVER
# ============================================================

if __name__ == '__main__':
    import os
    app.run(
        host='0.0.0.0',
        port=int(os.environ.get('PORT', '5000')),
        debug=False
    )