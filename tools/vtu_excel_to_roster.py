"""
Convert the class details spreadsheet (SI No., USN, Name, Email, Mobile Number)
into the EduLens roster CSV for Portal -> Students -> Import roster.

  pip install openpyxl
  python tools/vtu_excel_to_roster.py VTU_Details_7th_sem.xlsx roster_7th_sem.csv \
      --department CSD --semester 7 --section A

The student's own mobile goes into phone_number. Add parent_phone / parent_email
columns later (or edit in the portal); until then alerts go to the student.
Keep the output out of git: it contains personal data.
"""
import argparse
import csv
import re

import openpyxl


def clean_phone(value):
    if value is None:
        return ''
    digits = re.sub(r'\D', '', str(int(value)) if isinstance(value, float) else str(value))
    if len(digits) == 12 and digits.startswith('91'):
        digits = digits[2:]
    return digits if len(digits) == 10 else ''


def title_case(name):
    name = re.sub(r'\s+', ' ', str(name or '')).strip().rstrip('.')
    # keep initials ("N", "Y.R", "G.") upper-case, title-case the rest
    return ' '.join(w.upper() if (len(w) <= 2 or '.' in w) else w.capitalize() for w in name.split(' '))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('xlsx')
    ap.add_argument('out_csv')
    ap.add_argument('--department', default='CSD')
    ap.add_argument('--semester', default='7')
    ap.add_argument('--section', default='A')
    args = ap.parse_args()

    ws = openpyxl.load_workbook(args.xlsx, data_only=True).worksheets[0]
    header = [str(c or '').strip().lower() for c in next(ws.iter_rows(min_row=1, max_row=1, values_only=True))]
    col = lambda key: next(i for i, h in enumerate(header) if key in h)
    i_usn, i_name, i_email, i_phone = col('usn'), col('name'), col('email'), col('mobile')

    seen, written, skipped = set(), 0, []
    with open(args.out_csv, 'w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['usn', 'name', 'department', 'semester', 'section', 'email', 'phone_number',
                    'parent_name', 'parent_phone', 'parent_email'])
        for row in ws.iter_rows(min_row=2, values_only=True):
            usn = str(row[i_usn] or '').strip().upper()
            if not usn or usn in seen:
                continue
            seen.add(usn)
            name = title_case(row[i_name])
            if not name:
                skipped.append(usn)
                continue
            w.writerow([usn, name, args.department, args.semester, args.section,
                        str(row[i_email] or '').strip().lower(), clean_phone(row[i_phone]), '', '', ''])
            written += 1
    print(f'{written} students written to {args.out_csv}')
    if skipped:
        print(f'{len(skipped)} USNs skipped (no name in sheet): {", ".join(skipped)}')


if __name__ == '__main__':
    main()
