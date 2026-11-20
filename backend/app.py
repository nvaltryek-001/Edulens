import os
import io
import csv
import json
import uuid
from datetime import datetime

from flask import Flask, request, jsonify
from flask_cors import CORS

from werkzeug.utils import secure_filename
from werkzeug.security import generate_password_hash, check_password_hash

from ml_core import EduLensMLCore
from database import get_db_connection, init_db
from auth import generate_token, token_required
from services.notification_service import NotificationService
from services.parent_notifier import ParentNotifier
from portal_routes import portal, init_portal_tables


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
init_portal_tables()
app.register_blueprint(portal)

ml_core = EduLensMLCore(
    embeddings_path=os.environ.get('EDULENS_EMBEDDINGS_PATH', 'embeddings.pkl')
)

notification_service = NotificationService()

parent_notifier = ParentNotifier(get_db_connection)

STAFF_ROLES = ('administrator', 'hod', 'teacher', 'faculty')
ADMIN_ROLES = ('administrator', 'hod')
ALLOWED_IMAGE_EXTENSIONS = {'.jpg', '.jpeg', '.png'}

app.config['MAX_CONTENT_LENGTH'] = 25 * 1024 * 1024  # 25 MB per request


def find_user_by_student_login(login):
    login = str(login or '').strip().lower()
    if not login:
        return None
    conn = get_db_connection()
    try:
        student = conn.execute(
            'SELECT student_id FROM students WHERE LOWER(TRIM(email)) = ? OR LOWER(usn) = ?',
            (login, login)
        ).fetchone()
        if not student:
            return None
        return conn.execute(
            "SELECT * FROM users WHERE role = 'student' AND student_id = ?",
            (student['student_id'],)
        ).fetchone()
    finally:
        conn.close()


def allowed_image(filename):
    return os.path.splitext(filename or '')[1].lower() in ALLOWED_IMAGE_EXTENSIONS


def unique_upload_name(filename):
    ext = os.path.splitext(secure_filename(filename or ''))[1].lower() or '.jpg'
    return f'{uuid.uuid4().hex}{ext}'


def get_roster(conn, session):
    """Students who belong to the session's department, semester and section."""
    return conn.execute(
        '''
        SELECT * FROM students
        WHERE department = ? AND CAST(semester AS TEXT) = ? AND section = ?
        ORDER BY usn, name
        ''',
        (session['department'], str(session['semester']), session['section'])
    ).fetchall()


def can_manage_session(current_user, session):
    """Admins and HODs can manage any session; faculty only their own."""
    if current_user['role'] in ADMIN_ROLES:
        return True
    owner = session['created_by'] if 'created_by' in session.keys() else None
    return not owner or owner == current_user['username']


def session_to_json(row):
    if row is None:
        return None
    return {
        'sessionId': row['session_id'],
        'subject': row['subject'],
        'faculty': row['faculty'],
        'department': row['department'],
        'semester': row['semester'],
        'section': row['section'],
        'startTime': row['start_time'],
        'endTime': row['end_time'],
        'status': row['status'],
    }


def finalize_session(conn, session, present_ids, actor, manual_reason=''):
    """
    Write the official register for a session in one transaction:
    Present for confirmed students, Absent for everyone else on the roster.
    Unknown faces never make anyone absent; absence comes only from the roster.
    """
    session_id = session['session_id']
    roster_ids = [row['student_id'] for row in get_roster(conn, session)]
    roster_set = set(roster_ids)

    candidates = {
        row['student_id']: row['confidence']
        for row in conn.execute(
            'SELECT student_id, confidence FROM session_candidates WHERE session_id = ?',
            (session_id,)
        ).fetchall()
    }

    if present_ids is None:
        present = set(candidates) & roster_set
    else:
        present = {str(sid) for sid in present_ids}
        unknown = present - roster_set
        if unknown:
            raise ValueError(
                'These students are not on this section roster: ' + ', '.join(sorted(unknown))
            )

    manual_added = sorted(present - set(candidates))
    ai_rejected = sorted((set(candidates) & roster_set) - present)

    start = session['start_time'] or datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    date_part, _, time_part = start.partition(' ')

    conn.execute('DELETE FROM attendance WHERE session_id = ?', (session_id,))
    for student_id in roster_ids:
        is_present = student_id in present
        conn.execute(
            '''
            INSERT INTO attendance
            (student_id, date, time, status, subject, faculty, session_id, source, confidence)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''',
            (
                student_id, date_part, time_part or '00:00:00',
                'Present' if is_present else 'Absent',
                session['subject'], session['faculty'], session_id,
                ('AI' if student_id in candidates else 'MANUAL') if is_present else 'ROSTER',
                candidates.get(student_id)
            )
        )

    conn.execute(
        '''
        UPDATE attendance_sessions
        SET status = 'ENDED', end_time = ?, finalized_by = ?
        WHERE session_id = ?
        ''',
        (datetime.now().strftime('%Y-%m-%d %H:%M:%S'), actor, session_id)
    )

    conn.execute(
        'INSERT INTO logs (event) VALUES (?)',
        (json.dumps({
            'event': 'SESSION_CONFIRMED',
            'sessionId': session_id,
            'by': actor,
            'present': len(present),
            'absent': len(roster_ids) - len(present),
            'manualAdded': manual_added,
            'aiRejected': ai_rejected,
            'reason': manual_reason,
        }),)
    )
    conn.commit()

    return {
        'total': len(roster_ids),
        'present': len(present),
        'absent': len(roster_ids) - len(present),
        'manualAdded': manual_added,
        'aiRejected': ai_rejected,
    }




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
        # Students may sign in with their USN or college e-mail
        user = find_user_by_student_login(data['username'])

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
            'role': user['role'],
            'username': user['username']
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
                phone_number,
                parent_name,
                parent_phone,
                parent_email
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''',
            (
                data['student_id'],
                data['name'],
                data['usn'],
                data['department'],
                data['semester'],
                data['section'],
                data.get('email', ''),
                data.get('phone_number', ''),
                data.get('parent_name', ''),
                data.get('parent_phone', ''),
                data.get('parent_email', '')
            )
        )

        conn.commit()

    except KeyError as e:

        return jsonify({
            'success': False,
            'error': f'Missing field: {e}'
        }), 400

    except Exception:

        app.logger.exception('Add student failed')
        return jsonify({
            'success': False,
            'error': 'Could not add student (duplicate student_id?)'
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

    parent_name = request.form.get('parentName', '').strip()
    parent_phone = request.form.get('parentPhone', '').strip()
    parent_email = request.form.get('parentEmail', '').strip()

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
                    phone_number = ?,
                    parent_name = COALESCE(NULLIF(?, ''), parent_name),
                    parent_phone = COALESCE(NULLIF(?, ''), parent_phone),
                    parent_email = COALESCE(NULLIF(?, ''), parent_email)
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
                    parent_name,
                    parent_phone,
                    parent_email,
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
                    phone_number,
                    parent_name,
                    parent_phone,
                    parent_email
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ''',
                (
                    student_id,
                    name,
                    usn,
                    department,
                    semester,
                    section,
                    email,
                    phone_number,
                    parent_name,
                    parent_phone,
                    parent_email
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
    """
    Detect faces in one classroom photo and record them as CANDIDATES for the
    active session. Nothing is written to the attendance register here; the
    teacher confirms the final list with /api/attendance/confirm (or
    /api/sessions/end), which writes Present and Absent rows together.
    Call this up to four times per session (left, centre, right, back rows);
    a student seen in several photos is counted once.
    """

    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    if 'image' not in request.files or request.files['image'].filename == '':
        return jsonify({'success': False, 'error': 'No image file provided'}), 400

    file = request.files['image']

    fields = {
        key: request.form.get(key, '').strip()
        for key in ('subject', 'faculty', 'department', 'semester', 'section')
    }
    missing = [key for key, value in fields.items() if not value]
    if missing:
        return jsonify({
            'success': False,
            'error': 'Missing class information',
            'missingFields': missing
        }), 400

    if not allowed_image(file.filename):
        return jsonify({'success': False, 'error': 'Only JPG or PNG images are accepted'}), 400

    # Unique name: two teachers uploading "image.jpg" at once must not collide
    filepath = os.path.join(app.config['UPLOAD_FOLDER'], unique_upload_name(file.filename))
    file.save(filepath)

    conn = None
    try:
        conn = get_db_connection()

        active_session = conn.execute(
            '''
            SELECT * FROM attendance_sessions
            WHERE status = 'ACTIVE' AND subject = ? AND faculty = ?
              AND department = ? AND semester = ? AND section = ?
            ORDER BY session_id DESC LIMIT 1
            ''',
            (fields['subject'], fields['faculty'], fields['department'],
             fields['semester'], fields['section'])
        ).fetchone()

        if not active_session:
            return jsonify({
                'success': False,
                'students': [],
                'error': 'No active attendance session found for this class. '
                         'Start the attendance session first.'
            }), 400

        if not can_manage_session(current_user, active_session):
            return jsonify({'success': False, 'error': 'This session belongs to another faculty member'}), 403

        session_id = active_session['session_id']
        roster = get_roster(conn, active_session)
        student_db = {row['student_id']: dict(row) for row in roster}

        if not student_db:
            return jsonify({
                'success': False,
                'students': [],
                'error': 'No students are registered for this department, semester and section.'
            }), 400

        # Match ONLY against this section's roster
        students = ml_core.recognize_faces(
            filepath,
            student_db,
            allowed_student_ids=set(student_db.keys())
        )

        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        added, duplicates = [], []

        for student in students:
            student_id = student.get('studentId')
            if student_id not in student_db:
                continue
            cur = conn.execute(
                '''
                INSERT OR IGNORE INTO session_candidates
                (session_id, student_id, confidence, detected_at)
                VALUES (?, ?, ?, ?)
                ''',
                (session_id, student_id, student.get('confidence'), now)
            )
            if cur.rowcount:
                added.append(student_id)
            else:
                duplicates.append(student_id)
                conn.execute(
                    '''
                    UPDATE session_candidates SET confidence = MAX(COALESCE(confidence, 0), ?)
                    WHERE session_id = ? AND student_id = ?
                    ''',
                    (student.get('confidence') or 0, session_id, student_id)
                )
            student['attendanceStatus'] = 'Detected - confirm to save'

        conn.commit()

        candidate_count = conn.execute(
            'SELECT COUNT(*) FROM session_candidates WHERE session_id = ?', (session_id,)
        ).fetchone()[0]

        return jsonify({
            'success': True,
            'students': students,
            'attendanceAdded': added,
            'duplicatesSkipped': duplicates,
            'pendingReview': True,
            'sessionId': session_id,
            'rosterSize': len(student_db),
            'candidatesSoFar': candidate_count,
            'class': fields
        })

    except Exception:
        app.logger.exception('Recognition failed')
        if conn:
            conn.rollback()
        return jsonify({
            'success': False,
            'students': [],
            'error': 'Recognition failed. Check the photo and try again.'
        }), 500

    finally:
        if conn:
            conn.close()
        try:
            os.remove(filepath)
        except OSError:
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

    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'message': 'Unauthorized'}), 403

    data = request.json or {}
    fields = {
        key: str(data.get(key, '')).strip()
        for key in ('subject', 'faculty', 'department', 'semester', 'section')
    }
    for key, value in fields.items():
        if not value:
            return jsonify({'success': False, 'message': f'{key.capitalize()} is required'}), 400

    conn = get_db_connection()
    try:
        # Block only a clash for THIS faculty member or THIS section,
        # so other classrooms can run sessions at the same time.
        active_session = conn.execute(
            '''
            SELECT * FROM attendance_sessions
            WHERE status = 'ACTIVE'
              AND (
                    created_by = ?
                 OR (department = ? AND semester = ? AND section = ?)
              )
            ORDER BY session_id DESC LIMIT 1
            ''',
            (current_user['username'], fields['department'], fields['semester'], fields['section'])
        ).fetchone()

        if active_session:
            return jsonify({
                'success': False,
                'message': 'An attendance session is already active for you or for this section. '
                           'Confirm or end it first.',
                'session': session_to_json(active_session)
            }), 409

        start_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        cursor = conn.execute(
            '''
            INSERT INTO attendance_sessions
            (subject, faculty, department, semester, section, start_time, end_time, status, created_by)
            VALUES (?, ?, ?, ?, ?, ?, NULL, 'ACTIVE', ?)
            ''',
            (fields['subject'], fields['faculty'], fields['department'], fields['semester'],
             fields['section'], start_time, current_user['username'])
        )
        conn.commit()

        session = conn.execute(
            'SELECT * FROM attendance_sessions WHERE session_id = ?', (cursor.lastrowid,)
        ).fetchone()

        return jsonify({
            'success': True,
            'message': 'Attendance session started successfully',
            'session': session_to_json(session)
        }), 201

    except Exception:
        app.logger.exception('Failed to start session')
        return jsonify({'success': False, 'message': 'Failed to start attendance session'}), 500

    finally:
        conn.close()

# ============================================================
# A8 — END ATTENDANCE SESSION
# ============================================================

@app.route('/api/sessions/end', methods=['POST'])
@app.route('/api/attendance/confirm', methods=['POST'])
@token_required
def end_attendance_session(current_user):
    """
    Teacher confirms the register and closes the session.

    Body: {
      "sessionId": 12,
      "presentStudentIds": ["S001", "S007"],   optional - the reviewed list;
                                               if omitted, the AI candidates are used
      "manualReason": "Sitting behind pillar"   optional - why students were added by hand
    }

    Writes one Present or Absent row for EVERY student on the section roster,
    in one transaction, then sends absence alerts to parents in the background.
    """

    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'message': 'Unauthorized'}), 403

    data = request.json or {}
    session_id = data.get('sessionId')
    if not session_id:
        return jsonify({'success': False, 'message': 'sessionId is required'}), 400

    present_ids = data.get('presentStudentIds')
    if present_ids is not None and not isinstance(present_ids, list):
        return jsonify({'success': False, 'message': 'presentStudentIds must be a list'}), 400

    conn = get_db_connection()
    try:
        session = conn.execute(
            'SELECT * FROM attendance_sessions WHERE session_id = ?', (session_id,)
        ).fetchone()

        if not session:
            return jsonify({'success': False, 'message': 'Attendance session not found'}), 404

        if session['status'] == 'ENDED':
            return jsonify({
                'success': False,
                'message': 'Attendance session is already confirmed and locked'
            }), 409

        if not can_manage_session(current_user, session):
            return jsonify({'success': False, 'message': 'This session belongs to another faculty member'}), 403

        summary = finalize_session(
            conn,
            session,
            present_ids,
            actor=current_user['username'],
            manual_reason=str(data.get('manualReason', '')).strip()
        )

        updated = conn.execute(
            'SELECT * FROM attendance_sessions WHERE session_id = ?', (session_id,)
        ).fetchone()

    except ValueError as exc:
        conn.rollback()
        return jsonify({'success': False, 'message': str(exc)}), 400

    except Exception:
        conn.rollback()
        app.logger.exception('Failed to confirm session')
        return jsonify({'success': False, 'message': 'Failed to confirm attendance session'}), 500

    finally:
        conn.close()

    parent_notifier.notify_absentees_async(session_id)

    return jsonify({
        'success': True,
        'message': f"Attendance saved: {summary['present']} present, {summary['absent']} absent",
        'session': session_to_json(updated),
        'summary': summary
    })

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

    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    data = request.json or {}
    records = data.get('attendanceRecords')
    if not isinstance(records, list):
        return jsonify({'success': False, 'message': 'Invalid payload'}), 400

    conn = get_db_connection()
    synced_count = 0
    skipped = 0

    try:
        for record in records:
            student_id = record.get('studentId')
            session_id = record.get('sessionId')
            status = record.get('status')

            if not student_id or status not in ('Present', 'Absent'):
                skipped += 1
                continue

            if session_id:
                session = conn.execute(
                    'SELECT * FROM attendance_sessions WHERE session_id = ?', (session_id,)
                ).fetchone()
                # Offline records may not overwrite a confirmed, locked session
                if not session or session['status'] == 'ENDED' or not can_manage_session(current_user, session):
                    skipped += 1
                    continue
                existing = conn.execute(
                    'SELECT 1 FROM attendance WHERE session_id = ? AND student_id = ?',
                    (session_id, student_id)
                ).fetchone()
            else:
                existing = conn.execute(
                    '''
                    SELECT 1 FROM attendance
                    WHERE student_id = ? AND date = ? AND time = ? AND subject = ?
                    ''',
                    (student_id, record.get('date'), record.get('time'), record.get('subject'))
                ).fetchone()

            if existing:
                skipped += 1
                continue

            conn.execute(
                '''
                INSERT INTO attendance
                (student_id, date, time, status, subject, faculty, session_id, source)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'OFFLINE_SYNC')
                ''',
                (student_id, record.get('date'), record.get('time'), status,
                 record.get('subject'), record.get('faculty'), session_id)
            )
            synced_count += 1

        conn.commit()

    except Exception:
        conn.rollback()
        app.logger.exception('Sync failed')
        return jsonify({'success': False, 'message': 'Sync failed'}), 500

    finally:
        conn.close()

    return jsonify({
        'success': True,
        'message': f'Successfully synced {synced_count} records.',
        'synced': synced_count,
        'skipped': skipped
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
            '85'
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

    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

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
# TEACHER REVIEW - ROSTER WITH AI CANDIDATES
# ============================================================

@app.route('/api/sessions/<int:session_id>/review', methods=['GET'])
@token_required
def review_session(current_user, session_id):

    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    conn = get_db_connection()
    try:
        session = conn.execute(
            'SELECT * FROM attendance_sessions WHERE session_id = ?', (session_id,)
        ).fetchone()
        if not session:
            return jsonify({'success': False, 'error': 'Session not found'}), 404
        if not can_manage_session(current_user, session):
            return jsonify({'success': False, 'error': 'Unauthorized'}), 403

        candidates = {
            row['student_id']: row['confidence']
            for row in conn.execute(
                'SELECT student_id, confidence FROM session_candidates WHERE session_id = ?',
                (session_id,)
            ).fetchall()
        }
        saved = {
            row['student_id']: row['status']
            for row in conn.execute(
                'SELECT student_id, status FROM attendance WHERE session_id = ?', (session_id,)
            ).fetchall()
        }
        roster = [
            {
                'studentId': row['student_id'],
                'name': row['name'],
                'usn': row['usn'],
                'detected': row['student_id'] in candidates,
                'confidence': candidates.get(row['student_id']),
                'savedStatus': saved.get(row['student_id']),
            }
            for row in get_roster(conn, session)
        ]
        return jsonify({
            'success': True,
            'session': session_to_json(session),
            'rosterSize': len(roster),
            'detectedCount': sum(1 for r in roster if r['detected']),
            'students': roster,
        })
    finally:
        conn.close()


# ============================================================
# ROSTER IMPORT (CSV FROM EXAM CELL) AND PARENT CONTACTS
# ============================================================

@app.route('/api/students/import', methods=['POST'])
@token_required
def import_students(current_user):
    """
    Upload a CSV with a header row. Columns (case-insensitive):
    usn, name, department, semester, section, email, phone_number,
    parent_name, parent_phone, parent_email, student_id (defaults to usn).
    Existing students are updated; face embeddings are not touched.
    """

    if current_user['role'] not in ADMIN_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    if 'file' not in request.files:
        return jsonify({'success': False, 'error': 'Upload a CSV file in the "file" field'}), 400

    try:
        text = request.files['file'].read().decode('utf-8-sig')
    except UnicodeDecodeError:
        return jsonify({'success': False, 'error': 'CSV must be UTF-8 encoded'}), 400

    reader = csv.DictReader(io.StringIO(text))
    columns = ['name', 'usn', 'department', 'semester', 'section', 'email',
               'phone_number', 'parent_name', 'parent_phone', 'parent_email']
    inserted, updated, errors = 0, 0, []

    conn = get_db_connection()
    try:
        for line_no, raw in enumerate(reader, start=2):
            row = {(k or '').strip().lower(): (v or '').strip() for k, v in raw.items()}
            student_id = row.get('student_id') or row.get('usn')
            if not student_id or not row.get('name'):
                errors.append(f'line {line_no}: usn/student_id and name are required')
                continue
            values = [row.get(col, '') for col in columns]
            exists = conn.execute(
                'SELECT 1 FROM students WHERE student_id = ?', (student_id,)
            ).fetchone()
            if exists:
                conn.execute(
                    f"UPDATE students SET {', '.join(c + ' = ?' for c in columns)} WHERE student_id = ?",
                    values + [student_id]
                )
                updated += 1
            else:
                conn.execute(
                    f"INSERT INTO students (student_id, {', '.join(columns)}) "
                    f"VALUES ({', '.join('?' * (len(columns) + 1))})",
                    [student_id] + values
                )
                inserted += 1
        conn.commit()
    except Exception:
        conn.rollback()
        app.logger.exception('Roster import failed')
        return jsonify({'success': False, 'error': 'Import failed'}), 500
    finally:
        conn.close()

    return jsonify({'success': True, 'inserted': inserted, 'updated': updated, 'errors': errors[:50]})


@app.route('/api/students/<student_id>/parent', methods=['PUT'])
@token_required
def update_parent_contact(current_user, student_id):

    if current_user['role'] not in ADMIN_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    data = request.json or {}
    conn = get_db_connection()
    try:
        cur = conn.execute(
            '''
            UPDATE students SET parent_name = ?, parent_phone = ?, parent_email = ?
            WHERE student_id = ?
            ''',
            (str(data.get('parentName', '')).strip(), str(data.get('parentPhone', '')).strip(),
             str(data.get('parentEmail', '')).strip(), student_id)
        )
        conn.commit()
    finally:
        conn.close()

    if not cur.rowcount:
        return jsonify({'success': False, 'error': 'Student not found'}), 404
    return jsonify({'success': True, 'message': 'Parent contact updated'})


# ============================================================
# PARENT NOTIFICATIONS
# ============================================================

@app.route('/api/notifications/parents/status', methods=['GET'])
@token_required
def parent_notification_status(current_user):
    return jsonify({'success': True, **parent_notifier.status()})


@app.route('/api/notifications/parents/shortage', methods=['POST'])
@token_required
def send_shortage_alerts(current_user):
    """Alert parents of every student below 85% in a subject (once per student, subject and week)."""

    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    data = request.json or {}
    year, week, _ = datetime.now().isocalendar()
    results = parent_notifier.notify_shortages(
        department=data.get('department'),
        semester=data.get('semester'),
        section=data.get('section'),
        subject=data.get('subject'),
        threshold=data.get('threshold'),
        period_key=data.get('periodKey') or f'{year}-W{week:02d}',
    )
    return jsonify({
        'success': True,
        'sent': sum(1 for r in results if r['sent']),
        'failed': sum(1 for r in results if not r['sent']),
        'results': results,
    })


@app.route('/api/notifications/parents/absent/<int:session_id>', methods=['POST'])
@token_required
def resend_absent_alerts(current_user, session_id):
    """Run absence alerts for a confirmed session now (already-sent parents are skipped)."""

    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    results = parent_notifier.notify_absentees(session_id)
    return jsonify({
        'success': True,
        'sent': sum(1 for r in results if r['sent']),
        'failed': sum(1 for r in results if not r['sent']),
        'results': results,
    })


@app.route('/api/notifications/parents/test', methods=['POST'])
@token_required
def test_parent_notification(current_user):

    if current_user['role'] not in ADMIN_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    data = request.json or {}
    results = parent_notifier.send_test(phone=data.get('phone'), email=data.get('email'))
    return jsonify({
        'success': all(ok for ok, _ in results) if results else False,
        'results': [{'sent': ok, 'error': err} for ok, err in results],
        **parent_notifier.status(),
    })


@app.route('/api/notifications/parents', methods=['GET'])
@token_required
def list_parent_notifications(current_user):

    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    query = 'SELECT * FROM parent_notifications WHERE 1 = 1'
    params = []
    if request.args.get('studentId'):
        query += ' AND student_id = ?'
        params.append(request.args['studentId'])
    if request.args.get('kind'):
        query += ' AND kind = ?'
        params.append(request.args['kind'].upper())
    query += ' ORDER BY notification_id DESC LIMIT 200'

    conn = get_db_connection()
    try:
        rows = [dict(r) for r in conn.execute(query, params).fetchall()]
    finally:
        conn.close()
    return jsonify({'success': True, 'count': len(rows), 'notifications': rows})

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