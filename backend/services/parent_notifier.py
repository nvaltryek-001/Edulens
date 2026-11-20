"""
Parent notifications for EduLens.

Sends an SMS and/or e-mail to a student's parent when:
  * the student is marked ABSENT in a confirmed (finalized) session, or
  * the student's attendance in a subject falls below the VTU minimum (85%).

Every message is recorded in the `parent_notifications` table with a unique
(student_id, event_key, channel) key, so the same parent is never alerted twice
for the same event, even if a session is confirmed again or the job is re-run.

Configuration (environment variables)
-------------------------------------
PARENT_SMS_PROVIDER   log | android_gateway | fast2sms | msg91 | twilio   (default: log)
PARENT_EMAIL_ENABLED  true | false                         (default: false)

ANDROID_GATEWAY_URL, ANDROID_GATEWAY_USER, ANDROID_GATEWAY_PASSWORD
    Sends SMS through an Android phone's own SIM using the free open-source
    "SMS Gateway for Android" app (github.com/capcom6/android-sms-gateway):
    install it on the phone, start the Local Server, and set
    ANDROID_GATEWAY_URL=http://<phone-ip>:8080 with the user/password it shows.
NOTIFY_STUDENT_IF_NO_PARENT  true | false   (default: true)
    When a parent number/e-mail is missing, alert the student's own phone/e-mail.
FAST2SMS_API_KEY, FAST2SMS_ROUTE (default "q")
MSG91_AUTH_KEY, MSG91_FLOW_ID   (flow variables: name, usn, subject, date, percent)
TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER

SMTP_HOST, SMTP_PORT (587), SMTP_USER, SMTP_PASSWORD, SMTP_FROM

COLLEGE_SHORT_NAME (default "SVIT"), ATTENDANCE_MIN_PERCENT (default 85)

"log" mode writes the exact message to the server log and the database without
sending anything; use it for demos and testing.

Note: commercial SMS in India must use a DLT-registered sender ID and template
(TRAI rules). Register the two templates below with your SMS provider before
switching from "log" to a real provider.
"""

import base64
import json
import logging
import os
import re
import smtplib
import ssl
import threading
import urllib.parse
import urllib.request
from email.message import EmailMessage

log = logging.getLogger('edulens.parent_notifier')

COLLEGE = os.environ.get('COLLEGE_SHORT_NAME', 'SVIT')
MIN_PERCENT = float(os.environ.get('ATTENDANCE_MIN_PERCENT', '85'))

ABSENT_TEMPLATE = (
    'Dear Parent, your ward {name} ({usn}) was absent for {subject} '
    'on {date} at {time}. Attendance in {subject}: {percent}%. - {college}'
)

SHORTAGE_TEMPLATE = (
    'Dear Parent, attendance of your ward {name} ({usn}) in {subject} is '
    '{percent}%, below the required {minimum}%. Please contact the class '
    'teacher. - {college}'
)


STUDENT_ABSENT_TEMPLATE = (
    'Dear {name} ({usn}), you were marked absent for {subject} on {date} at {time}. '
    'Attendance in {subject}: {percent}%. - {college}'
)

STUDENT_SHORTAGE_TEMPLATE = (
    'Dear {name} ({usn}), your attendance in {subject} is {percent}%, below the required '
    '{minimum}%. Attend the next {must} classes to recover. - {college}'
)


def _clean_phone(phone):
    """Return a 10-digit Indian mobile number, or None."""
    if not phone:
        return None
    digits = re.sub(r'\D', '', str(phone))
    if len(digits) == 12 and digits.startswith('91'):
        digits = digits[2:]
    if len(digits) == 11 and digits.startswith('0'):
        digits = digits[1:]
    if len(digits) == 10 and digits[0] in '6789':
        return digits
    return None


def _post(url, data=None, headers=None, json_body=None, timeout=15):
    if json_body is not None:
        body = json.dumps(json_body).encode()
        headers = {**(headers or {}), 'Content-Type': 'application/json'}
    else:
        body = urllib.parse.urlencode(data or {}).encode()
    req = urllib.request.Request(url, data=body, headers=headers or {}, method='POST')
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.status, resp.read().decode(errors='replace')


class ParentNotifier:

    def __init__(self, get_connection):
        self.get_connection = get_connection
        self.sms_provider = os.environ.get('PARENT_SMS_PROVIDER', 'log').lower()
        self.email_enabled = os.environ.get('PARENT_EMAIL_ENABLED', 'false').lower() == 'true'

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def status(self):
        return {
            'smsProvider': self.sms_provider,
            'emailEnabled': self.email_enabled,
            'minimumPercent': MIN_PERCENT,
        }

    def notify_absentees_async(self, session_id):
        """Send absence alerts for a finalized session without blocking the request."""
        t = threading.Thread(target=self._safe, args=(self.notify_absentees, session_id), daemon=True)
        t.start()

    def notify_absentees(self, session_id):
        conn = self.get_connection()
        try:
            session = conn.execute(
                'SELECT * FROM attendance_sessions WHERE session_id = ?', (session_id,)
            ).fetchone()
            if not session:
                return []
            rows = conn.execute(
                '''
                SELECT s.*, a.date, a.time
                FROM attendance a JOIN students s ON s.student_id = a.student_id
                WHERE a.session_id = ? AND a.status = 'Absent'
                ''',
                (session_id,)
            ).fetchall()
            results = []
            for row in rows:
                percent = self._subject_percent(conn, row['student_id'], session['subject'])
                fields = dict(
                    name=row['name'], usn=row['usn'] or row['student_id'],
                    subject=session['subject'], date=row['date'], time=(row['time'] or '')[:5],
                    percent=percent, college=COLLEGE,
                )
                results += self._deliver(conn, row, f'absent:{session_id}', 'ABSENT',
                                         ABSENT_TEMPLATE.format(**fields),
                                         STUDENT_ABSENT_TEMPLATE.format(**fields))
            return results
        finally:
            conn.close()

    def notify_shortages(self, department=None, semester=None, section=None, subject=None,
                         threshold=None, period_key=None):
        """Alert parents of students below the threshold in any subject.

        period_key groups alerts (e.g. ISO week "2026-W47"); a parent gets at most one
        shortage message per student, subject and period.
        """
        threshold = MIN_PERCENT if threshold is None else float(threshold)
        conn = self.get_connection()
        try:
            query = '''
                SELECT s.student_id, s.name, s.usn, s.parent_name, s.parent_phone, s.parent_email,
                       s.phone_number, s.email,
                       sess.subject AS subject,
                       SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS present,
                       COUNT(a.attendance_id) AS total
                FROM attendance a
                JOIN attendance_sessions sess ON sess.session_id = a.session_id
                JOIN students s ON s.student_id = a.student_id
                WHERE sess.status = 'ENDED'
            '''
            params = []
            for col, val in (('s.department', department), ('s.semester', semester),
                             ('s.section', section), ('sess.subject', subject)):
                if val:
                    query += f' AND {col} = ?'
                    params.append(val)
            query += ' GROUP BY s.student_id, sess.subject'
            results = []
            for row in conn.execute(query, params).fetchall():
                if not row['total']:
                    continue
                percent = round(100.0 * row['present'] / row['total'], 1)
                if percent >= threshold:
                    continue
                m = threshold / 100.0
                must = max(0, -(-(m * row['total'] - row['present']) // (1 - m)))
                fields = dict(
                    name=row['name'], usn=row['usn'] or row['student_id'], subject=row['subject'],
                    percent=percent, minimum=int(threshold), college=COLLEGE, must=int(must),
                )
                key = f'shortage:{row["subject"]}:{period_key or "now"}'
                results += self._deliver(conn, row, key, 'SHORTAGE',
                                         SHORTAGE_TEMPLATE.format(**fields),
                                         STUDENT_SHORTAGE_TEMPLATE.format(**fields))
            return results
        finally:
            conn.close()

    def send_test(self, phone=None, email=None):
        text = f'EduLens test message from {COLLEGE}. Parent alerts are working.'
        out = []
        if phone:
            out.append(self._send_sms(_clean_phone(phone), text))
        if email:
            out.append(self._send_email(email, f'{COLLEGE} attendance alert (test)', text))
        return out

    # ------------------------------------------------------------------
    # Internals
    # ------------------------------------------------------------------

    def _safe(self, fn, *args):
        try:
            fn(*args)
        except Exception:
            log.exception('Parent notification job failed')

    @staticmethod
    def _subject_percent(conn, student_id, subject):
        row = conn.execute(
            '''
            SELECT SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS present,
                   COUNT(*) AS total
            FROM attendance a JOIN attendance_sessions s ON s.session_id = a.session_id
            WHERE a.student_id = ? AND s.subject = ? AND s.status = 'ENDED'
            ''',
            (student_id, subject)
        ).fetchone()
        if not row or not row['total']:
            return 0
        return round(100.0 * row['present'] / row['total'], 1)

    def _deliver(self, conn, student, event_key, kind, text, student_text=None):
        """Send to the parent; if no parent contact, to the student (when enabled)."""
        keys = student.keys()
        get = lambda k: (student[k] if k in keys else None)
        fallback = os.environ.get('NOTIFY_STUDENT_IF_NO_PARENT', 'true').lower() == 'true'

        targets = []
        parent_phone = _clean_phone(get('parent_phone'))
        if parent_phone:
            targets.append(('SMS', parent_phone, text))
        elif fallback and student_text and _clean_phone(get('phone_number')):
            targets.append(('SMS', _clean_phone(get('phone_number')), student_text))
        if self.email_enabled:
            parent_email = (get('parent_email') or '').strip()
            if parent_email:
                targets.append(('EMAIL', parent_email, text))
            elif fallback and student_text and (get('email') or '').strip():
                targets.append(('EMAIL', get('email').strip(), student_text))

        results = []
        for channel, recipient, message in targets:
            # Reserve the slot first; the UNIQUE key makes repeats a no-op
            cur = conn.execute(
                '''
                INSERT OR IGNORE INTO parent_notifications
                (student_id, event_key, kind, channel, recipient, message, status)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                ''',
                (student['student_id'], event_key, kind, channel, recipient, message)
            )
            conn.commit()
            if cur.rowcount == 0:
                continue
            if channel == 'SMS':
                ok, error = self._send_sms(recipient, message)
            else:
                ok, error = self._send_email(recipient, f'{COLLEGE} attendance alert', message)
            conn.execute(
                '''
                UPDATE parent_notifications SET status = ?, error = ?
                WHERE student_id = ? AND event_key = ? AND channel = ?
                ''',
                ('SENT' if ok else 'FAILED', error, student['student_id'], event_key, channel)
            )
            conn.commit()
            results.append({'studentId': student['student_id'], 'channel': channel,
                            'recipient': recipient, 'sent': ok, 'error': error})
        return results

    def notify_weekly_summaries(self, period_key, department=None, semester=None, section=None):
        """E-mail every student their subject-wise attendance (once per week)."""
        if not self.email_enabled:
            return []
        conn = self.get_connection()
        try:
            query = "SELECT * FROM students WHERE TRIM(COALESCE(email, '')) != ''"
            params = []
            for col, val in (('department', department), ('semester', semester), ('section', section)):
                if val:
                    query += f' AND CAST({col} AS TEXT) = ?'
                    params.append(str(val))
            results = []
            for st in conn.execute(query, params).fetchall():
                rows = conn.execute(
                    '''
                    SELECT s.subject, SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS present,
                           COUNT(*) AS total
                    FROM attendance a JOIN attendance_sessions s ON s.session_id = a.session_id
                    WHERE a.student_id = ? AND s.status = 'ENDED'
                    GROUP BY s.subject ORDER BY s.subject
                    ''',
                    (st['student_id'],)
                ).fetchall()
                if not rows:
                    continue
                m = MIN_PERCENT / 100.0
                lines = []
                for r in rows:
                    pct = round(100.0 * r['present'] / r['total'], 1)
                    if pct >= MIN_PERCENT:
                        note = f"can miss {int(r['present'] / m - r['total'] + 1e-9)} more"
                    else:
                        need = -(-(m * r['total'] - r['present']) // (1 - m))
                        note = f'below {int(MIN_PERCENT)}%: attend the next {int(need)}'
                    lines.append(f"  {r['subject']:<10} {r['present']}/{r['total']}  {pct}%  ({note})")
                body = (f"Dear {st['name']},\n\nYour attendance this week ({period_key}):\n\n"
                        + '\n'.join(lines)
                        + f"\n\nThe VTU minimum is {int(MIN_PERCENT)}% in each subject.\n"
                          f"Portal: {os.environ.get('PORTAL_URL', '')}\n\n- {COLLEGE} EduLens")
                key = f'weekly:{period_key}'
                cur = conn.execute(
                    '''
                    INSERT OR IGNORE INTO parent_notifications
                    (student_id, event_key, kind, channel, recipient, message, status)
                    VALUES (?, ?, 'WEEKLY', 'EMAIL', ?, ?, 'PENDING')
                    ''',
                    (st['student_id'], key, st['email'].strip(), body)
                )
                conn.commit()
                if cur.rowcount == 0:
                    continue
                ok, error = self._send_email(st['email'].strip(), f'{COLLEGE} weekly attendance', body)
                conn.execute(
                    "UPDATE parent_notifications SET status = ?, error = ? "
                    "WHERE student_id = ? AND event_key = ? AND channel = 'EMAIL'",
                    ('SENT' if ok else 'FAILED', error, st['student_id'], key)
                )
                conn.commit()
                results.append({'studentId': st['student_id'], 'channel': 'EMAIL',
                                'recipient': st['email'].strip(), 'sent': ok, 'error': error})
            return results
        finally:
            conn.close()

    def _send_sms(self, phone, text):
        if not phone:
            return False, 'invalid phone number'
        provider = self.sms_provider
        try:
            if provider == 'log':
                print(f'[PARENT SMS to {phone}] {text}', flush=True)
                return True, None
            if provider == 'android_gateway':
                url = os.environ['ANDROID_GATEWAY_URL'].rstrip('/')
                user = os.environ.get('ANDROID_GATEWAY_USER', '')
                pwd = os.environ.get('ANDROID_GATEWAY_PASSWORD', '')
                auth = base64.b64encode(f'{user}:{pwd}'.encode()).decode()
                status, body = _post(
                    f'{url}/message',
                    json_body={'message': text, 'phoneNumbers': ['+91' + phone]},
                    headers={'Authorization': f'Basic {auth}'},
                )
                ok = status in (200, 201, 202)
                return ok, None if ok else body[:300]
            if provider == 'fast2sms':
                status, body = _post(
                    'https://www.fast2sms.com/dev/bulkV2',
                    json_body={'route': os.environ.get('FAST2SMS_ROUTE', 'q'),
                               'message': text, 'language': 'english', 'numbers': phone},
                    headers={'authorization': os.environ['FAST2SMS_API_KEY']},
                )
                ok = status == 200 and '"return":true' in body.replace(' ', '')
                return ok, None if ok else body[:300]
            if provider == 'msg91':
                status, body = _post(
                    'https://control.msg91.com/api/v5/flow/',
                    json_body={'template_id': os.environ['MSG91_FLOW_ID'],
                               'recipients': [{'mobiles': '91' + phone, 'message': text}]},
                    headers={'authkey': os.environ['MSG91_AUTH_KEY']},
                )
                ok = status == 200 and '"type":"success"' in body.replace(' ', '')
                return ok, None if ok else body[:300]
            if provider == 'twilio':
                sid = os.environ['TWILIO_ACCOUNT_SID']
                token = os.environ['TWILIO_AUTH_TOKEN']
                auth = base64.b64encode(f'{sid}:{token}'.encode()).decode()
                status, body = _post(
                    f'https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json',
                    data={'To': '+91' + phone, 'From': os.environ['TWILIO_FROM_NUMBER'], 'Body': text},
                    headers={'Authorization': f'Basic {auth}'},
                )
                ok = status in (200, 201)
                return ok, None if ok else body[:300]
            return False, f'unknown SMS provider "{provider}"'
        except KeyError as missing:
            return False, f'missing setting {missing}'
        except Exception as exc:
            log.warning('SMS send failed: %s', exc)
            return False, str(exc)[:300]

    def _send_email(self, address, subject, text):
        if not address:
            return False, 'no e-mail address'
        host = os.environ.get('SMTP_HOST')
        if not host:
            return False, 'SMTP_HOST not configured'
        try:
            msg = EmailMessage()
            msg['From'] = os.environ.get('SMTP_FROM', os.environ.get('SMTP_USER', ''))
            msg['To'] = address
            msg['Subject'] = subject
            msg.set_content(text)
            with smtplib.SMTP(host, int(os.environ.get('SMTP_PORT', '587')), timeout=20) as smtp:
                smtp.starttls(context=ssl.create_default_context())
                if os.environ.get('SMTP_USER'):
                    smtp.login(os.environ['SMTP_USER'], os.environ.get('SMTP_PASSWORD', ''))
                smtp.send_message(msg)
            return True, None
        except Exception as exc:
            log.warning('E-mail send failed: %s', exc)
            return False, str(exc)[:300]
