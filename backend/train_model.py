import argparse
from ml_core import EduLensMLCore

def main():
    parser = argparse.ArgumentParser(description="Train EduLens ML Model")
    parser.add_argument("--dataset", default="dataset", help="Path to dataset directory")
    args = parser.parse_args()

    ml_core = EduLensMLCore()
    
    print(f"Starting training on dataset: {args.dataset}")
    success, num_classes = ml_core.train(args.dataset)
    
    if success:
        print(f"Training completed successfully. Model trained on {num_classes} classes.")
    else:
        print("Training failed. Ensure the dataset contains face images in the correct directory structure (dataset/student_id/image.jpg).")

if __name__ == "__main__":
    main()
