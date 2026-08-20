import os
import pickle
from typing import Dict, List, Tuple, Any

import cv2
import numpy as np
from insightface.app import FaceAnalysis


class EduLensMLCore:
    """
    EduLens facial-recognition core.

    Pipeline:

        Image
          ↓
        InsightFace buffalo_l
          ↓
        Face detection
          ↓
        Face quality checks
          ↓
        512-dimensional embedding
          ↓
        Compare against enrolled embeddings
          ↓
        Best + second-best similarity
          ↓
        Confidence / margin validation
          ↓
        Student identification
    """

    def __init__(
        self,
        model_path: str = "model.pkl",
        embeddings_path: str = "embeddings.pkl",
        similarity_threshold: float = 0.45
    ):

        self.model_path = model_path
        self.embeddings_path = embeddings_path
        self.similarity_threshold = similarity_threshold

        # =========================================================
        # QUALITY SETTINGS
        # =========================================================

        # Minimum detected face width/height.
        # Small faces are less reliable.
        self.min_face_size = 70

        # Blur threshold using Laplacian variance.
        # Lower values indicate blurrier images.
        self.min_blur_score = 35.0

        # Brightness limits.
        self.min_brightness = 35.0
        self.max_brightness = 235.0

        # Minimum difference between best and second-best match.
        # Helps reject ambiguous matches.
        self.min_similarity_margin = 0.03

        # =========================================================
        # INSIGHTFACE
        # =========================================================

        self.face_app = FaceAnalysis(
            name="buffalo_l",
            providers=["CPUExecutionProvider"]
        )

        self.face_app.prepare(
            ctx_id=0,
            det_size=(640, 640)
        )

        # =========================================================
        # STUDENT EMBEDDING GALLERY
        # =========================================================

        self.student_embeddings: Dict[
            str,
            List[np.ndarray]
        ] = {}

        self.load_embeddings()

    # =============================================================
    # EMBEDDING STORAGE
    # =============================================================

    def load_embeddings(self):
        """
        Load enrolled student embeddings from embeddings.pkl.
        """

        if not os.path.exists(self.embeddings_path):
            self.student_embeddings = {}
            return

        try:

            with open(
                self.embeddings_path,
                "rb"
            ) as f:

                data = pickle.load(f)

            if isinstance(data, dict):
                self.student_embeddings = data
            else:
                self.student_embeddings = {}

            print(
                f"EDULENS_ML: Loaded embeddings for "
                f"{len(self.student_embeddings)} students"
            )

        except Exception as e:

            print(
                f"EDULENS_ML: Could not load embeddings: {e}"
            )

            self.student_embeddings = {}

    # -------------------------------------------------------------

    def save_embeddings(self):
        """
        Save all enrolled student embeddings.
        """

        with open(
            self.embeddings_path,
            "wb"
        ) as f:

            pickle.dump(
                self.student_embeddings,
                f,
                protocol=pickle.HIGHEST_PROTOCOL
            )

        print(
            f"EDULENS_ML: Saved embeddings for "
            f"{len(self.student_embeddings)} students"
        )

    # =============================================================
    # IMAGE QUALITY
    # =============================================================

    @staticmethod
    def calculate_blur_score(
        image: np.ndarray
    ) -> float:
        """
        Calculate blur/sharpness score.

        Higher = sharper.
        Lower = blurrier.
        """

        if image is None or image.size == 0:
            return 0.0

        if len(image.shape) == 3:

            gray = cv2.cvtColor(
                image,
                cv2.COLOR_BGR2GRAY
            )

        else:

            gray = image

        return float(
            cv2.Laplacian(
                gray,
                cv2.CV_64F
            ).var()
        )

    # -------------------------------------------------------------

    @staticmethod
    def calculate_brightness(
        image: np.ndarray
    ) -> float:
        """
        Calculate average image brightness.
        """

        if image is None or image.size == 0:
            return 0.0

        if len(image.shape) == 3:

            gray = cv2.cvtColor(
                image,
                cv2.COLOR_BGR2GRAY
            )

        else:

            gray = image

        return float(
            np.mean(gray)
        )

    # -------------------------------------------------------------

    def check_face_quality(
        self,
        face_crop: np.ndarray
    ) -> Tuple[bool, Dict[str, float]]:
        """
        Check:

        1. Face size
        2. Blur
        3. Brightness
        """

        if face_crop is None or face_crop.size == 0:

            return False, {
                "faceWidth": 0.0,
                "faceHeight": 0.0,
                "blurScore": 0.0,
                "brightness": 0.0
            }

        height, width = face_crop.shape[:2]

        blur_score = self.calculate_blur_score(
            face_crop
        )

        brightness = self.calculate_brightness(
            face_crop
        )

        quality = {
            "faceWidth": float(width),
            "faceHeight": float(height),
            "blurScore": float(blur_score),
            "brightness": float(brightness)
        }

        # ---------------------------------------------------------
        # FACE SIZE
        # ---------------------------------------------------------

        if (
            width < self.min_face_size
            or height < self.min_face_size
        ):

            print(
                f"EDULENS_QUALITY: Face too small "
                f"({width}x{height})"
            )

            return False, quality

        # ---------------------------------------------------------
        # BLUR
        # ---------------------------------------------------------

        if blur_score < self.min_blur_score:

            print(
                f"EDULENS_QUALITY: Face too blurry "
                f"(score={blur_score:.2f})"
            )

            return False, quality

        # ---------------------------------------------------------
        # DARK IMAGE
        # ---------------------------------------------------------

        if brightness < self.min_brightness:

            print(
                f"EDULENS_QUALITY: Face too dark "
                f"(brightness={brightness:.2f})"
            )

            return False, quality

        # ---------------------------------------------------------
        # OVEREXPOSED IMAGE
        # ---------------------------------------------------------

        if brightness > self.max_brightness:

            print(
                f"EDULENS_QUALITY: Face overexposed "
                f"(brightness={brightness:.2f})"
            )

            return False, quality

        return True, quality

    # =============================================================
    # IMAGE / FACE DETECTION
    # =============================================================

    def detect_faces(
        self,
        image_path: str
    ) -> List[
        Tuple[
            np.ndarray,
            Tuple[int, int, int, int],
            Any
        ]
    ]:
        """
        Detect all faces in an image.

        Returns:

            [
                (
                    face_crop,
                    (x1, y1, x2, y2),
                    insightface_face_object
                )
            ]
        """

        image = cv2.imread(
            image_path
        )

        if image is None:

            print(
                f"EDULENS_ML: Unable to read image: "
                f"{image_path}"
            )

            return []

        faces = self.face_app.get(
            image
        )

        results = []

        height, width = image.shape[:2]

        for face in faces:

            bbox = face.bbox.astype(int)

            x1 = max(
                0,
                int(bbox[0])
            )

            y1 = max(
                0,
                int(bbox[1])
            )

            x2 = min(
                width,
                int(bbox[2])
            )

            y2 = min(
                height,
                int(bbox[3])
            )

            if x2 <= x1 or y2 <= y1:
                continue

            face_crop = image[
                y1:y2,
                x1:x2
            ]

            results.append(
                (
                    face_crop,
                    (
                        x1,
                        y1,
                        x2,
                        y2
                    ),
                    face
                )
            )

        return results

    # =============================================================
    # EMBEDDING GENERATION
    # =============================================================

    @staticmethod
    def normalize_embedding(
        embedding: np.ndarray
    ) -> np.ndarray:
        """
        L2-normalize an embedding.
        """

        embedding = np.asarray(
            embedding,
            dtype=np.float32
        )

        norm = np.linalg.norm(
            embedding
        )

        if norm <= 0:
            return embedding

        return embedding / norm

    # -------------------------------------------------------------

    def generate_embedding_from_face(
        self,
        face_object
    ) -> np.ndarray:
        """
        Generate a normalized 512-dimensional embedding.
        """

        if face_object is None:

            raise ValueError(
                "Invalid face object"
            )

        embedding = getattr(
            face_object,
            "embedding",
            None
        )

        if embedding is None:

            raise ValueError(
                "InsightFace did not generate an embedding"
            )

        embedding = np.asarray(
            embedding,
            dtype=np.float32
        )

        if embedding.shape != (512,):

            raise ValueError(
                f"Unexpected embedding shape: "
                f"{embedding.shape}"
            )

        return self.normalize_embedding(
            embedding
        )

    # =============================================================
    # SIMILARITY
    # =============================================================

    @staticmethod
    def cosine_similarity(
        embedding_a: np.ndarray,
        embedding_b: np.ndarray
    ) -> float:
        """
        Calculate cosine similarity.
        """

        a = np.asarray(
            embedding_a,
            dtype=np.float32
        )

        b = np.asarray(
            embedding_b,
            dtype=np.float32
        )

        a_norm = np.linalg.norm(a)
        b_norm = np.linalg.norm(b)

        if a_norm <= 0 or b_norm <= 0:
            return 0.0

        return float(
            np.dot(a, b) /
            (a_norm * b_norm)
        )

    # =============================================================
    # ENROLL ONE STUDENT
    # =============================================================

    def enroll_student(
        self,
        student_id: str,
        image_paths: List[str]
    ) -> Tuple[bool, int]:
        """
        Generate embeddings for enrollment images.

        Existing embeddings for this student are replaced.
        """

        if not student_id:

            raise ValueError(
                "student_id is required"
            )

        if not image_paths:
            return False, 0

        embeddings = []

        for image_path in image_paths:

            if not os.path.exists(image_path):

                print(
                    f"EDULENS_ENROLL: File not found: "
                    f"{image_path}"
                )

                continue

            image = cv2.imread(
                image_path
            )

            if image is None:

                print(
                    f"EDULENS_ENROLL: Could not read: "
                    f"{image_path}"
                )

                continue

            faces = self.face_app.get(
                image
            )

            if len(faces) != 1:

                print(
                    f"EDULENS_ENROLL: Skipping "
                    f"{os.path.basename(image_path)} "
                    f"because {len(faces)} faces were detected"
                )

                continue

            face_object = faces[0]

            bbox = face_object.bbox.astype(int)

            x1 = max(
                0,
                int(bbox[0])
            )

            y1 = max(
                0,
                int(bbox[1])
            )

            x2 = min(
                image.shape[1],
                int(bbox[2])
            )

            y2 = min(
                image.shape[0],
                int(bbox[3])
            )

            face_crop = image[
                y1:y2,
                x1:x2
            ]

            # -----------------------------------------------------
            # Enrollment quality check
            # -----------------------------------------------------

            quality_ok, quality = (
                self.check_face_quality(
                    face_crop
                )
            )

            if not quality_ok:

                print(
                    f"EDULENS_ENROLL: Skipping "
                    f"low-quality image: "
                    f"{image_path}"
                )

                continue

            try:

                embedding = (
                    self.generate_embedding_from_face(
                        face_object
                    )
                )

                embeddings.append(
                    embedding
                )

            except Exception as e:

                print(
                    f"EDULENS_ENROLL: Embedding error "
                    f"for {image_path}: {e}"
                )

        if not embeddings:
            return False, 0

        self.student_embeddings[
            student_id
        ] = embeddings

        self.save_embeddings()

        print(
            f"EDULENS_ENROLL: {student_id} enrolled with "
            f"{len(embeddings)} embeddings"
        )

        return True, len(embeddings)

    # =============================================================
    # TRAIN / BUILD EMBEDDING GALLERY
    # =============================================================

    def train(
        self,
        dataset_dir: str
    ) -> Tuple[bool, int]:
        """
        Build the student embedding gallery.

        This generates embeddings. It does not train buffalo_l.
        """

        if not os.path.exists(
            dataset_dir
        ):

            print(
                f"EDULENS_ML: Dataset directory not found: "
                f"{dataset_dir}"
            )

            return False, 0

        new_gallery = {}

        supported_extensions = (
            ".jpg",
            ".jpeg",
            ".png",
            ".webp"
        )

        for student_id in os.listdir(
            dataset_dir
        ):

            student_path = os.path.join(
                dataset_dir,
                student_id
            )

            if not os.path.isdir(
                student_path
            ):
                continue

            image_paths = []

            for filename in os.listdir(
                student_path
            ):

                if not filename.lower().endswith(
                    supported_extensions
                ):
                    continue

                image_paths.append(
                    os.path.join(
                        student_path,
                        filename
                    )
                )

            if not image_paths:
                continue

            embeddings = []

            for image_path in image_paths:

                image = cv2.imread(
                    image_path
                )

                if image is None:
                    continue

                faces = self.face_app.get(
                    image
                )

                if len(faces) != 1:

                    print(
                        f"EDULENS_ML: Skipping "
                        f"{image_path}: detected "
                        f"{len(faces)} faces"
                    )

                    continue

                face_object = faces[0]

                bbox = face_object.bbox.astype(int)

                x1 = max(
                    0,
                    int(bbox[0])
                )

                y1 = max(
                    0,
                    int(bbox[1])
                )

                x2 = min(
                    image.shape[1],
                    int(bbox[2])
                )

                y2 = min(
                    image.shape[0],
                    int(bbox[3])
                )

                face_crop = image[
                    y1:y2,
                    x1:x2
                ]

                quality_ok, quality = (
                    self.check_face_quality(
                        face_crop
                    )
                )

                if not quality_ok:

                    print(
                        f"EDULENS_ML: Skipping "
                        f"low-quality training image: "
                        f"{image_path}"
                    )

                    continue

                try:

                    embedding = (
                        self.generate_embedding_from_face(
                            face_object
                        )
                    )

                    embeddings.append(
                        embedding
                    )

                except Exception as e:

                    print(
                        f"EDULENS_ML: Failed embedding "
                        f"{image_path}: {e}"
                    )

            if embeddings:

                new_gallery[
                    student_id
                ] = embeddings

                print(
                    f"EDULENS_ML: {student_id}: "
                    f"{len(embeddings)} embeddings"
                )

        if not new_gallery:

            print(
                "EDULENS_ML: No valid student embeddings generated"
            )

            return False, 0

        self.student_embeddings = new_gallery

        self.save_embeddings()

        return True, len(new_gallery)

    # =============================================================
    # RECOGNITION
    # =============================================================

    def recognize_faces(
        self,
        image_path: str,
        student_database: Dict[
            str,
            Dict[str, Any]
        ]
    ) -> List[Dict[str, Any]]:
        """
        Detect and recognize all faces.

        Added protections:

        - Small-face rejection
        - Blur rejection
        - Brightness rejection
        - Best-match calculation
        - Second-best calculation
        - Similarity margin validation
        - Unknown-face rejection
        - Duplicate student removal
        """

        self.load_embeddings()

        if not self.student_embeddings:

            raise Exception(
                "No student face embeddings are enrolled yet."
            )

        detected_faces = self.detect_faces(
            image_path
        )

        recognized_students = []

        print(
            f"EDULENS_RECOGNITION: "
            f"Detected {len(detected_faces)} faces"
        )

        for face_index, (
            face_crop,
            bbox,
            face_object
        ) in enumerate(
            detected_faces
        ):

            # =====================================================
            # QUALITY CHECK
            # =====================================================

            quality_ok, quality = (
                self.check_face_quality(
                    face_crop
                )
            )

            if not quality_ok:

                print(
                    f"EDULENS_RECOGNITION: "
                    f"Face {face_index + 1} rejected "
                    f"by quality filter"
                )

                continue

            print(
                f"EDULENS_RECOGNITION: "
                f"Face {face_index + 1} quality OK | "
                f"size="
                f"{int(quality['faceWidth'])}x"
                f"{int(quality['faceHeight'])} | "
                f"blur="
                f"{quality['blurScore']:.2f} | "
                f"brightness="
                f"{quality['brightness']:.2f}"
            )

            # =====================================================
            # EMBEDDING
            # =====================================================

            try:

                query_embedding = (
                    self.generate_embedding_from_face(
                        face_object
                    )
                )

            except Exception as e:

                print(
                    f"EDULENS_RECOGNITION: "
                    f"Face {face_index + 1} embedding "
                    f"failed: {e}"
                )

                continue

            # =====================================================
            # BEST MATCH FOR EACH STUDENT
            # =====================================================

            student_best_scores = {}

            for student_id, embeddings in (
                self.student_embeddings.items()
            ):

                best_student_similarity = -1.0

                for stored_embedding in embeddings:

                    similarity = (
                        self.cosine_similarity(
                            query_embedding,
                            stored_embedding
                        )
                    )

                    if (
                        similarity >
                        best_student_similarity
                    ):

                        best_student_similarity = (
                            similarity
                        )

                student_best_scores[
                    student_id
                ] = best_student_similarity

            if not student_best_scores:

                continue

            # =====================================================
            # SORT CANDIDATES
            # =====================================================

            sorted_candidates = sorted(
                student_best_scores.items(),
                key=lambda item: item[1],
                reverse=True
            )

            best_student_id = (
                sorted_candidates[0][0]
            )

            best_similarity = (
                sorted_candidates[0][1]
            )

            if len(sorted_candidates) > 1:

                second_student_id = (
                    sorted_candidates[1][0]
                )

                second_similarity = (
                    sorted_candidates[1][1]
                )

            else:

                second_student_id = None
                second_similarity = 0.0

            similarity_margin = (
                best_similarity -
                second_similarity
            )

            print(
                f"EDULENS_RECOGNITION: "
                f"Face {face_index + 1} | "
                f"best={best_student_id} "
                f"{best_similarity:.4f} | "
                f"second={second_student_id} "
                f"{second_similarity:.4f} | "
                f"margin={similarity_margin:.4f}"
            )

            # =====================================================
            # CONFIDENCE THRESHOLD
            # =====================================================

            if (
                best_similarity <
                self.similarity_threshold
            ):

                print(
                    f"EDULENS_RECOGNITION: "
                    f"Face {face_index + 1} rejected "
                    f"as unknown. "
                    f"Similarity="
                    f"{best_similarity:.4f}, "
                    f"threshold="
                    f"{self.similarity_threshold:.4f}"
                )

                continue

            # =====================================================
            # AMBIGUOUS MATCH CHECK
            # =====================================================

            if (
                len(sorted_candidates) > 1
                and similarity_margin <
                self.min_similarity_margin
            ):

                print(
                    f"EDULENS_RECOGNITION: "
                    f"Face {face_index + 1} rejected "
                    f"as ambiguous. "
                    f"Margin="
                    f"{similarity_margin:.4f}, "
                    f"required="
                    f"{self.min_similarity_margin:.4f}"
                )

                continue

            # =====================================================
            # DATABASE LOOKUP
            # =====================================================

            student_info = (
                student_database.get(
                    best_student_id
                )
            )

            if not student_info:

                print(
                    f"EDULENS_RECOGNITION: "
                    f"Student {best_student_id} "
                    f"not found in database"
                )

                continue

            # =====================================================
            # ACCEPT MATCH
            # =====================================================

            recognized_students.append(
                {
                    "name": student_info.get(
                        "name"
                    ),

                    "studentId": best_student_id,

                    "usn": student_info.get(
                        "usn"
                    ),

                    "department": student_info.get(
                        "department"
                    ),

                    "attendanceStatus": "Present",

                    "bbox": bbox,

                    "confidence": float(
                        best_similarity
                    ),

                    # Extra diagnostic information.
                    # Existing app code can ignore these.
                    "quality": quality,

                    "secondBestSimilarity": float(
                        second_similarity
                    ),

                    "similarityMargin": float(
                        similarity_margin
                    )
                }
            )

        # =========================================================
        # REMOVE DUPLICATE STUDENTS
        # =========================================================

        unique_students = {}

        for student in recognized_students:

            student_id = student.get(
                "studentId"
            )

            if not student_id:
                continue

            previous = unique_students.get(
                student_id
            )

            if (
                previous is None
                or student["confidence"]
                > previous["confidence"]
            ):

                unique_students[
                    student_id
                ] = student

        # =========================================================
        # FINAL RESULT
        # =========================================================

        final_students = list(
            unique_students.values()
        )

        final_students.sort(
            key=lambda x: x["confidence"],
            reverse=True
        )

        print(
            f"EDULENS_RECOGNITION: "
            f"{len(final_students)} unique students recognized"
        )

        return final_students

    # =============================================================
    # UTILITY
    # =============================================================

    def get_enrolled_student_count(
        self
    ) -> int:
        """
        Return number of students currently enrolled.
        """

        self.load_embeddings()

        return len(
            self.student_embeddings
        )

    # -------------------------------------------------------------

    def clear_student_embeddings(
        self,
        student_id: str
    ) -> bool:
        """
        Remove one student's embeddings.
        """

        if (
            student_id
            not in self.student_embeddings
        ):

            return False

        del self.student_embeddings[
            student_id
        ]

        self.save_embeddings()

        return True