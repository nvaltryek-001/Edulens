import argparse
from ml_core import EduLensMLCore
from database import get_db_connection

def main():
    parser = argparse.ArgumentParser(description="Test Facial Recognition Model")
    parser.add_argument("--image", required=True, help="Path to classroom image")
    args = parser.parse_args()

    ml_core = EduLensMLCore()
    
    conn = get_db_connection()
    rows = conn.execute('SELECT * FROM students').fetchall()
    conn.close()
    
    student_db = {row['student_id']: dict(row) for row in rows}
    
    try:
        students = ml_core.recognize_faces(args.image, student_db)
        print(f"Recognized {len(students)} students:")
        for student in students:
            print(f"- {student['name']} ({student['studentId']}) [Conf: {student['confidence']:.2f}]")
    except Exception as e:
        print(f"Error during recognition: {e}")

if __name__ == "__main__":
    main()
