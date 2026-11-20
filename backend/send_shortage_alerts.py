"""
Weekly job (run every Monday):
  1. SMS/e-mail parents (or the student, if no parent contact) of everyone
     below 85% in any subject - once per student, subject and ISO week.
  2. E-mail every student their subject-wise attendance summary
     (needs PARENT_EMAIL_ENABLED=true and SMTP settings).

Cron (every Monday 09:00):
  0 9 * * 1  cd /path/to/backend && python send_shortage_alerts.py
Docker:
  docker compose exec backend python send_shortage_alerts.py
"""
import argparse
from datetime import datetime

from database import get_db_connection, init_db
from services.parent_notifier import ParentNotifier


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--department')
    parser.add_argument('--semester')
    parser.add_argument('--section')
    parser.add_argument('--threshold', type=float)
    args = parser.parse_args()

    init_db()
    year, week, _ = datetime.now().isocalendar()
    results = ParentNotifier(get_db_connection).notify_shortages(
        department=args.department, semester=args.semester, section=args.section,
        threshold=args.threshold, period_key=f'{year}-W{week:02d}',
    )
    sent = sum(1 for r in results if r['sent'])
    print(f'Shortage alerts: {sent} sent, {len(results) - sent} failed')

    weekly = ParentNotifier(get_db_connection).notify_weekly_summaries(
        f'{year}-W{week:02d}', department=args.department, semester=args.semester, section=args.section)
    ok = sum(1 for r in weekly if r['sent'])
    print(f'Weekly student e-mails: {ok} sent, {len(weekly) - ok} failed')
    results += weekly
    for r in results:
        if not r['sent']:
            print(f"  FAILED {r['studentId']} {r['channel']} {r['recipient']}: {r['error']}")


if __name__ == '__main__':
    main()
