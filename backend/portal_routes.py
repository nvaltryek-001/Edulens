"""
Student portal, IA marks, assignment deadlines and account management.

Student accounts: an administrator creates a user with role "student" and the
student's student_id (usually the USN). The student then logs in with
/login and sees only their own records.
"""

import math
import os
from datetime import datetime

from flask import Blueprint, jsonify, request
from werkzeug.security import generate_password_hash

from auth import token_required
from database import get_db_connection, _add_column_if_missing

portal = Blueprint('portal', __name__)

STAFF_ROLES = ('administrator', 'hod', 'teacher', 'faculty')
ADMIN_ROLES = ('administrator', 'hod')
ALL_ROLES = ('administrator', 'hod', 'teacher', 'faculty', 'student')
MIN_PERCENT = float(os.environ.get('ATTENDANCE_MIN_PERCENT', '85'))


def init_portal_tables():
    conn = get_db_connection()
    try:
        _add_column_if_missing(conn, 'users', 'student_id', 'TEXT')
        conn.execute('''
            CREATE TABLE IF NOT EXISTS ia_marks (
                student_id TEXT NOT NULL,
                subject TEXT NOT NULL,
                ia_no INTEGER NOT NULL,
                marks REAL NOT NULL,
                max_marks REAL NOT NULL DEFAULT 50,
                entered_by TEXT,
                entered_at TEXT,
                PRIMARY KEY (student_id, subject, ia_no)
            )
        ''')
        conn.execute('''
            CREATE TABLE IF NOT EXISTS assignments (
                assignment_id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject TEXT NOT NULL,
                department TEXT NOT NULL,
                semester TEXT NOT NULL,
                section TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                deadline TEXT NOT NULL,
                created_by TEXT,
                created_at TEXT
            )
        ''')
        conn.commit()
    finally:
        conn.close()


def attendance_projection(present, total, minimum=MIN_PERCENT):
    """
    Returns (percent, can_miss, must_attend):
    can_miss    = classes the student can still skip and stay at or above the minimum
    must_attend = consecutive classes needed to get back to the minimum
    """
    if total == 0:
        return 0.0, 0, 0
    m = minimum / 100.0
    percent = round(100.0 * present / total, 1)
    if present / total >= m:
        can_miss = max(0, math.floor(present / m - total + 1e-9))
        return percent, can_miss, 0
    must_attend = math.ceil((m * total - present) / (1 - m) - 1e-9)
    return percent, 0, max(0, must_attend)


def _student_for(conn, current_user):
    row = conn.execute(
        'SELECT student_id FROM users WHERE username = ?', (current_user['username'],)
    ).fetchone()
    student_id = (row['student_id'] if row else None) or current_user['username']
    return conn.execute('SELECT * FROM students WHERE student_id = ? OR usn = ?',
                        (student_id, student_id)).fetchone()


# ------------------------------------------------------------------
# ACCOUNTS
# ------------------------------------------------------------------

@portal.route('/api/users', methods=['POST'])
@token_required
def create_user(current_user):
    """Administrator creates faculty, HOD or student accounts."""
    if current_user['role'] != 'administrator':
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    data = request.json or {}
    username = str(data.get('username', '')).strip()
    password = str(data.get('password', ''))
    role = str(data.get('role', '')).strip().lower()

    if not username or len(password) < 8 or role not in ALL_ROLES:
        return jsonify({'success': False,
                        'error': 'username, password (8+ characters) and a valid role are required'}), 400

    conn = get_db_connection()
    try:
        if conn.execute('SELECT 1 FROM users WHERE username = ?', (username,)).fetchone():
            return jsonify({'success': False, 'error': 'Username already exists'}), 409
        conn.execute(
            'INSERT INTO users (username, password_hash, role, student_id) VALUES (?, ?, ?, ?)',
            (username, generate_password_hash(password), role,
             str(data.get('studentId', '')).strip() or None)
        )
        conn.commit()
    finally:
        conn.close()
    return jsonify({'success': True, 'message': f'{role} account created'}), 201


# ------------------------------------------------------------------
# STUDENT PORTAL
# ------------------------------------------------------------------

@portal.route('/api/me/attendance', methods=['GET'])
@token_required
def my_attendance(current_user):
    """Subject-wise attendance for the logged-in student (or ?studentId= for staff)."""
    conn = get_db_connection()
    try:
        if current_user['role'] == 'student':
            student = _student_for(conn, current_user)
        elif current_user['role'] in STAFF_ROLES and request.args.get('studentId'):
            student = conn.execute('SELECT * FROM students WHERE student_id = ?',
                                   (request.args['studentId'],)).fetchone()
        else:
            return jsonify({'success': False, 'error': 'Unauthorized'}), 403

        if not student:
            return jsonify({'success': False, 'error': 'Student record not found'}), 404

        rows = conn.execute(
            '''
            SELECT s.subject,
                   SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS present,
                   COUNT(*) AS total,
                   MAX(a.date) AS last_class
            FROM attendance a JOIN attendance_sessions s ON s.session_id = a.session_id
            WHERE a.student_id = ? AND s.status = 'ENDED'
            GROUP BY s.subject ORDER BY s.subject
            ''',
            (student['student_id'],)
        ).fetchall()

        subjects = []
        tot_p = tot_t = 0
        for r in rows:
            pct, can_miss, must = attendance_projection(r['present'], r['total'])
            tot_p += r['present']
            tot_t += r['total']
            subjects.append({
                'subject': r['subject'], 'present': r['present'], 'total': r['total'],
                'percent': pct, 'canMiss': can_miss, 'mustAttend': must,
                'shortage': pct < MIN_PERCENT, 'lastClass': r['last_class'],
            })
        overall = round(100.0 * tot_p / tot_t, 1) if tot_t else 0.0

        return jsonify({
            'success': True,
            'student': {k: student[k] for k in ('student_id', 'name', 'usn', 'department', 'semester', 'section')},
            'minimumPercent': MIN_PERCENT,
            'overallPercent': overall,
            'subjects': subjects,
        })
    finally:
        conn.close()


@portal.route('/api/me/marks', methods=['GET'])
@token_required
def my_marks(current_user):
    if current_user['role'] != 'student':
        return jsonify({'success': False, 'error': 'Students only'}), 403
    conn = get_db_connection()
    try:
        student = _student_for(conn, current_user)
        if not student:
            return jsonify({'success': False, 'error': 'Student record not found'}), 404
        rows = [dict(r) for r in conn.execute(
            'SELECT subject, ia_no, marks, max_marks FROM ia_marks WHERE student_id = ? ORDER BY subject, ia_no',
            (student['student_id'],)
        ).fetchall()]
        return jsonify({'success': True, 'marks': rows})
    finally:
        conn.close()


@portal.route('/api/me/assignments', methods=['GET'])
@token_required
def my_assignments(current_user):
    if current_user['role'] != 'student':
        return jsonify({'success': False, 'error': 'Students only'}), 403
    conn = get_db_connection()
    try:
        student = _student_for(conn, current_user)
        if not student:
            return jsonify({'success': False, 'error': 'Student record not found'}), 404
        rows = [dict(r) for r in conn.execute(
            '''
            SELECT assignment_id, subject, title, description, deadline FROM assignments
            WHERE department = ? AND semester = ? AND section = ? AND deadline >= ?
            ORDER BY deadline
            ''',
            (student['department'], str(student['semester']), student['section'],
             datetime.now().strftime('%Y-%m-%d'))
        ).fetchall()]
        return jsonify({'success': True, 'assignments': rows})
    finally:
        conn.close()


# ------------------------------------------------------------------
# IA MARKS (faculty)
# ------------------------------------------------------------------

@portal.route('/api/ia-marks', methods=['POST'])
@token_required
def save_ia_marks(current_user):
    """
    Body: {"subject": "BCS701", "iaNo": 1, "maxMarks": 50,
           "marks": [{"studentId": "S1", "marks": 38}, ...]}
    """
    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403
    data = request.json or {}
    subject = str(data.get('subject', '')).strip()
    try:
        ia_no = int(data.get('iaNo'))
        max_marks = float(data.get('maxMarks', 50))
    except (TypeError, ValueError):
        return jsonify({'success': False, 'error': 'iaNo and maxMarks must be numbers'}), 400
    entries = data.get('marks') or []
    if not subject or not isinstance(entries, list):
        return jsonify({'success': False, 'error': 'subject and marks list are required'}), 400

    saved, errors = 0, []
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    conn = get_db_connection()
    try:
        for e in entries:
            try:
                marks = float(e.get('marks'))
            except (TypeError, ValueError):
                errors.append(f"{e.get('studentId')}: marks must be a number")
                continue
            if not 0 <= marks <= max_marks:
                errors.append(f"{e.get('studentId')}: marks out of range")
                continue
            conn.execute(
                '''
                INSERT INTO ia_marks (student_id, subject, ia_no, marks, max_marks, entered_by, entered_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(student_id, subject, ia_no)
                DO UPDATE SET marks = excluded.marks, max_marks = excluded.max_marks,
                              entered_by = excluded.entered_by, entered_at = excluded.entered_at
                ''',
                (e.get('studentId'), subject, ia_no, marks, max_marks, current_user['username'], now)
            )
            saved += 1
        conn.commit()
    finally:
        conn.close()
    return jsonify({'success': True, 'saved': saved, 'errors': errors})


@portal.route('/api/ia-marks', methods=['GET'])
@token_required
def list_ia_marks(current_user):
    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403
    query = '''
        SELECT m.*, s.name, s.usn FROM ia_marks m JOIN students s ON s.student_id = m.student_id
        WHERE 1 = 1
    '''
    params = []
    for arg, col in (('subject', 'm.subject'), ('department', 's.department'),
                     ('semester', 's.semester'), ('section', 's.section'), ('iaNo', 'm.ia_no')):
        if request.args.get(arg):
            query += f' AND {col} = ?'
            params.append(request.args[arg])
    query += ' ORDER BY s.usn, m.ia_no'
    conn = get_db_connection()
    try:
        rows = [dict(r) for r in conn.execute(query, params).fetchall()]
    finally:
        conn.close()
    return jsonify({'success': True, 'count': len(rows), 'marks': rows})


# ------------------------------------------------------------------
# ASSIGNMENT DEADLINES (faculty)
# ------------------------------------------------------------------

@portal.route('/api/assignments', methods=['POST'])
@token_required
def create_assignment(current_user):
    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403
    data = request.json or {}
    fields = {k: str(data.get(k, '')).strip()
              for k in ('subject', 'department', 'semester', 'section', 'title', 'deadline')}
    missing = [k for k, v in fields.items() if not v]
    if missing:
        return jsonify({'success': False, 'error': 'Missing: ' + ', '.join(missing)}), 400
    try:
        datetime.strptime(fields['deadline'][:10], '%Y-%m-%d')
    except ValueError:
        return jsonify({'success': False, 'error': 'deadline must be YYYY-MM-DD'}), 400

    conn = get_db_connection()
    try:
        cur = conn.execute(
            '''
            INSERT INTO assignments (subject, department, semester, section, title, description,
                                     deadline, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''',
            (fields['subject'], fields['department'], fields['semester'], fields['section'],
             fields['title'], str(data.get('description', '')).strip(), fields['deadline'],
             current_user['username'], datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
        )
        conn.commit()
    finally:
        conn.close()
    return jsonify({'success': True, 'assignmentId': cur.lastrowid}), 201


@portal.route('/api/assignments', methods=['GET'])
@token_required
def list_assignments(current_user):
    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403
    query = 'SELECT * FROM assignments WHERE 1 = 1'
    params = []
    for arg in ('subject', 'department', 'semester', 'section'):
        if request.args.get(arg):
            query += f' AND {arg} = ?'
            params.append(request.args[arg])
    query += ' ORDER BY deadline'
    conn = get_db_connection()
    try:
        rows = [dict(r) for r in conn.execute(query, params).fetchall()]
    finally:
        conn.close()
    return jsonify({'success': True, 'assignments': rows})


# ------------------------------------------------------------------
# ROSTER LIST (staff)
# ------------------------------------------------------------------

@portal.route('/api/students', methods=['GET'])
@token_required
def list_students(current_user):
    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403
    query = '''
        SELECT student_id, name, usn, department, semester, section, email,
               parent_name, parent_phone, parent_email
        FROM students WHERE 1 = 1
    '''
    params = []
    for arg in ('department', 'semester', 'section'):
        if request.args.get(arg):
            query += f' AND CAST({arg} AS TEXT) = ?'
            params.append(request.args[arg])
    query += ' ORDER BY department, semester, section, usn'
    conn = get_db_connection()
    try:
        rows = [dict(r) for r in conn.execute(query, params).fetchall()]
    finally:
        conn.close()
    return jsonify({'success': True, 'count': len(rows), 'students': rows})


# ------------------------------------------------------------------
# WEB PORTAL (same server, any browser)
# ------------------------------------------------------------------

@portal.route('/portal', methods=['GET'])
@portal.route('/', methods=['GET'])
def web_portal():
    from flask import send_from_directory
    return send_from_directory(
        os.path.join(os.path.dirname(os.path.abspath(__file__)), 'static', 'portal'),
        'index.html'
    )


# ------------------------------------------------------------------
# PER-SUBJECT SHORTAGE (VTU applies 85% to each course separately)
# ------------------------------------------------------------------

@portal.route('/api/shortage', methods=['GET'])
@token_required
def subject_shortage(current_user):
    if current_user['role'] not in STAFF_ROLES:
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403
    try:
        threshold = float(request.args.get('threshold', MIN_PERCENT))
    except ValueError:
        return jsonify({'success': False, 'error': 'threshold must be a number'}), 400

    query = '''
        SELECT st.student_id, st.name, st.usn, st.department, st.semester, st.section,
               st.parent_phone, s.subject,
               SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS present,
               COUNT(*) AS total
        FROM attendance a
        JOIN attendance_sessions s ON s.session_id = a.session_id
        JOIN students st ON st.student_id = a.student_id
        WHERE s.status = 'ENDED'
    '''
    params = []
    for arg, col in (('department', 'st.department'), ('semester', 'st.semester'),
                     ('section', 'st.section'), ('subject', 's.subject')):
        if request.args.get(arg):
            query += f' AND CAST({col} AS TEXT) = ?'
            params.append(request.args[arg])
    query += ' GROUP BY st.student_id, s.subject ORDER BY st.usn, s.subject'

    conn = get_db_connection()
    try:
        rows = conn.execute(query, params).fetchall()
    finally:
        conn.close()

    out = []
    for r in rows:
        pct, _, must = attendance_projection(r['present'], r['total'], threshold)
        if pct < threshold:
            out.append({
                'studentId': r['student_id'], 'name': r['name'], 'usn': r['usn'],
                'department': r['department'], 'semester': r['semester'], 'section': r['section'],
                'subject': r['subject'], 'present': r['present'], 'total': r['total'],
                'percent': pct, 'mustAttend': must, 'parentPhoneOnFile': bool(r['parent_phone']),
            })
    return jsonify({'success': True, 'threshold': threshold, 'count': len(out), 'students': out})


# ------------------------------------------------------------------
# STUDENT ACCOUNTS IN BULK + PASSWORD CHANGE
# ------------------------------------------------------------------

def _temp_password():
    import secrets
    alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
    return ''.join(secrets.choice(alphabet) for _ in range(10))


@portal.route('/api/students/create-accounts', methods=['POST'])
@token_required
def create_student_accounts(current_user):
    """
    Create a login for every student on the roster who has none.
    Username = USN; students may also sign in with their college e-mail.
    Returns the temporary passwords ONCE (they are stored only as hashes);
    with "sendEmail": true they are also e-mailed to each student.
    """
    if current_user['role'] != 'administrator':
        return jsonify({'success': False, 'error': 'Unauthorized'}), 403

    data = request.json or {}
    query = '''
        SELECT s.student_id, s.usn, s.name, s.email FROM students s
        WHERE NOT EXISTS (
            SELECT 1 FROM users u WHERE u.student_id = s.student_id OR u.username = s.usn
        )
    '''
    params = []
    for arg in ('department', 'semester', 'section'):
        if data.get(arg):
            query += f' AND CAST(s.{arg} AS TEXT) = ?'
            params.append(str(data[arg]))

    created = []
    conn = get_db_connection()
    try:
        for s in conn.execute(query, params).fetchall():
            username = (s['usn'] or s['student_id']).strip()
            password = _temp_password()
            conn.execute(
                'INSERT INTO users (username, password_hash, role, student_id) VALUES (?, ?, ?, ?)',
                (username, generate_password_hash(password), 'student', s['student_id'])
            )
            created.append({'usn': username, 'name': s['name'], 'email': (s['email'] or '').strip(),
                            'password': password})
        conn.commit()
    finally:
        conn.close()

    emailed = 0
    if data.get('sendEmail') and created:
        from services.parent_notifier import ParentNotifier
        notifier = ParentNotifier(get_db_connection)
        portal_url = os.environ.get('PORTAL_URL', request.host_url.rstrip('/') + '/portal')
        for c in created:
            if not c['email']:
                continue
            ok, _ = notifier._send_email(
                c['email'], 'Your EduLens attendance account',
                f"Dear {c['name']},\n\nYour EduLens account is ready.\n\n"
                f"Portal: {portal_url}\nLogin: {c['usn']} (or {c['email']})\n"
                f"Temporary password: {c['password']}\n\n"
                "Please change your password after signing in.\n\n- SVIT EduLens"
            )
            emailed += 1 if ok else 0

    return jsonify({'success': True, 'created': len(created), 'emailed': emailed, 'accounts': created})


@portal.route('/api/me/password', methods=['POST'])
@token_required
def change_password(current_user):
    from werkzeug.security import check_password_hash
    data = request.json or {}
    new = str(data.get('newPassword', ''))
    if len(new) < 8:
        return jsonify({'success': False, 'error': 'New password must be at least 8 characters'}), 400
    conn = get_db_connection()
    try:
        user = conn.execute('SELECT * FROM users WHERE username = ?', (current_user['username'],)).fetchone()
        if not user or not check_password_hash(user['password_hash'], str(data.get('currentPassword', ''))):
            return jsonify({'success': False, 'error': 'Current password is incorrect'}), 400
        conn.execute('UPDATE users SET password_hash = ? WHERE user_id = ?',
                     (generate_password_hash(new), user['user_id']))
        conn.commit()
    finally:
        conn.close()
    return jsonify({'success': True, 'message': 'Password changed'})
