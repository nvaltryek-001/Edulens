"""
Weekly job: SMS/e-mail parents of every student below 85% in any subject.
Each parent gets at most one message per student, subject and ISO week.

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
    for r in results:
        if not r['sent']:
            print(f"  FAILED {r['studentId']} {r['channel']} {r['recipient']}: {r['error']}")


if __name__ == '__main__':
    main()
