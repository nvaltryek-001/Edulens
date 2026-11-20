"""
End-to-end API test for the attendance flow with the face model stubbed out.
Run from backend/:  python -m unittest tests.test_attendance_flow -v
"""
import os
import sys
import tempfile
import types
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
BACKEND = os.path.dirname(HERE)
sys.path.insert(0, BACKEND)

TMP = tempfile.mkdtemp()
os.environ['EDULENS_DB_PATH'] = os.path.join(TMP, 'test.db')
os.environ['PARENT_SMS_PROVIDER'] = 'log'
os.environ.pop('APP_ENV', None)

# ---- stubs for heavy / optional dependencies ---------------------------
if 'flask_cors' not in sys.modules:
    try:
        import flask_cors  # noqa: F401
    except ImportError:
        sys.modules['flask_cors'] = types.SimpleNamespace(CORS=lambda app, **kw: None)

fake_firebase = types.ModuleType('firebase_admin')
fake_firebase._apps = {}
fake_firebase.credentials = types.SimpleNamespace(Certificate=lambda p: None)
fake_firebase.messaging = types.SimpleNamespace()
fake_firebase.initialize_app = lambda *a, **k: None
sys.modules.setdefault('firebase_admin', fake_firebase)
sys.modules.setdefault('firebase_admin.credentials', fake_firebase.credentials)
sys.modules.setdefault('firebase_admin.messaging', fake_firebase.messaging)

DETECTED = []  # student ids the fake model "sees" in the next photo


class FakeML:
    def __init__(self, *a, **k):
        self.last_allowed = None

    def recognize_faces(self, path, db, allowed_student_ids=None):
        self.last_allowed = allowed_student_ids
        out = []
        for sid in DETECTED:
            if allowed_student_ids is not None and sid not in allowed_student_ids:
                continue
            s = db.get(sid, {})
            out.append({'studentId': sid, 'name': s.get('name', sid), 'usn': s.get('usn', ''),
                        'department': s.get('department', ''), 'attendanceStatus': 'Present',
                        'confidence': 0.7})
        return out


sys.modules['ml_core'] = types.SimpleNamespace(EduLensMLCore=FakeML)

import app as edulens  # noqa: E402
from database import get_db_connection  # noqa: E402
from werkzeug.security import generate_password_hash  # noqa: E402


def make_user(username, role):
    conn = get_db_connection()
    conn.execute('INSERT OR IGNORE INTO users (username, password_hash, role) VALUES (?, ?, ?)',
                 (username, generate_password_hash('pass123'), role))
    conn.commit()
    conn.close()


class AttendanceFlowTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.c = edulens.app.test_client()
        make_user('admin', 'administrator')
        make_user('prof_a', 'teacher')
        make_user('prof_b', 'teacher')
        cls.admin = cls.login('admin')
        cls.prof_a = cls.login('prof_a')
        cls.prof_b = cls.login('prof_b')
        for i, sec in [(1, 'A'), (2, 'A'), (3, 'A'), (4, 'B')]:
            r = cls.c.post('/api/students', headers=cls.admin, json={
                'student_id': f'S{i}', 'name': f'Student {i}', 'usn': f'1VA23CD00{i}',
                'department': 'CSD', 'semester': '7', 'section': sec,
                'email': '', 'phone_number': '', 'parent_phone': f'98450000{i:02d}'})
            assert r.status_code == 200, r.json

    @classmethod
    def login(cls, user):
        r = cls.c.post('/login', json={'username': user, 'password': 'pass123'})
        return {'Authorization': 'Bearer ' + r.json['token']}

    def start(self, headers, section, subject='BCS701'):
        return self.c.post('/api/sessions/start', headers=headers, json={
            'subject': subject, 'faculty': 'Prof', 'department': 'CSD',
            'semester': '7', 'section': section})

    def photo(self, headers, section, subject='BCS701'):
        import io
        return self.c.post('/api/recognize', headers=headers, data={
            'image': (io.BytesIO(b'fake'), 'image.jpg'), 'subject': subject, 'faculty': 'Prof',
            'department': 'CSD', 'semester': '7', 'section': section},
            content_type='multipart/form-data')

    def test_full_flow(self):
        # two teachers can run sessions in different sections at the same time
        ra = self.start(self.prof_a, 'A')
        rb = self.start(self.prof_b, 'B')
        self.assertEqual(ra.status_code, 201, ra.json)
        self.assertEqual(rb.status_code, 201, rb.json)
        sid = ra.json['session']['sessionId']

        # photo sees S1 and S4 (S4 is another section and must be ignored)
        DETECTED[:] = ['S1', 'S4']
        r = self.photo(self.prof_a, 'A')
        self.assertTrue(r.json['success'], r.json)
        self.assertEqual(r.json['attendanceAdded'], ['S1'])
        self.assertEqual(edulens.ml_core.last_allowed, {'S1', 'S2', 'S3'})

        # recognition alone writes nothing to the register
        conn = get_db_connection()
        self.assertEqual(conn.execute('SELECT COUNT(*) FROM attendance WHERE session_id=?', (sid,)).fetchone()[0], 0)
        conn.close()

        # second photo: S1 again (duplicate) and S2
        DETECTED[:] = ['S1', 'S2']
        r = self.photo(self.prof_a, 'A')
        self.assertEqual(r.json['duplicatesSkipped'], ['S1'])

        # review shows roster with detected flags
        r = self.c.get(f'/api/sessions/{sid}/review', headers=self.prof_a)
        self.assertEqual(r.json['rosterSize'], 3)
        self.assertEqual(r.json['detectedCount'], 2)

        # another teacher cannot confirm prof_a's session
        r = self.c.post('/api/attendance/confirm', headers=self.prof_b, json={'sessionId': sid})
        self.assertEqual(r.status_code, 403)

        # teacher unchecks S2, adds S3 by hand
        r = self.c.post('/api/attendance/confirm', headers=self.prof_a, json={
            'sessionId': sid, 'presentStudentIds': ['S1', 'S3'], 'manualReason': 'behind pillar'})
        self.assertTrue(r.json['success'], r.json)
        self.assertEqual(r.json['summary']['present'], 2)
        self.assertEqual(r.json['summary']['absent'], 1)
        self.assertEqual(r.json['summary']['manualAdded'], ['S3'])
        self.assertEqual(r.json['summary']['aiRejected'], ['S2'])

        # locked after confirmation
        r = self.c.post('/api/sessions/end', headers=self.prof_a, json={'sessionId': sid})
        self.assertEqual(r.status_code, 409)

        # absent parent alert recorded once
        edulens.parent_notifier.notify_absentees(sid)
        edulens.parent_notifier.notify_absentees(sid)
        r = self.c.get('/api/notifications/parents?studentId=S2', headers=self.prof_a)
        self.assertEqual(r.json['count'], 1)
        self.assertEqual(r.json['notifications'][0]['status'], 'SENT')
        self.assertIn('absent', r.json['notifications'][0]['message'])

        # percentages now include absences: S2 0%
        r = self.c.get('/api/low-attendance?department=CSD&semester=7&section=A', headers=self.prof_a)
        self.assertEqual(r.status_code, 200)

        # shortage alerts: S2 (0%) is below 85%
        r = self.c.post('/api/notifications/parents/shortage', headers=self.prof_a, json={'section': 'A'})
        self.assertEqual(r.json['sent'], 1, r.json)

        # end prof_b's session with no detections: everyone absent
        r = self.c.post('/api/sessions/end', headers=self.prof_b, json={'sessionId': rb.json['session']['sessionId']})
        self.assertEqual(r.json['summary']['absent'], 1)


if __name__ == '__main__':
    unittest.main()
