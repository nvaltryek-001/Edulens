# Face Enrollment and AI

## Guided enrollment
Six poses: straight ahead, left, right, slightly upward, slightly downward, centered neutral. Validate exactly one face, size, sharpness, exposure, pose, centering, occlusion and completeness. Require identity-bound authorized enrollment and applicable college approval/notice.

## Profile lifecycle
Track CORE, ADAPTIVE and HISTORY/RETIRED samples. Version profiles with reason, source, model/config version, actor, timestamps and rollback. Weekly checks never blindly replace trusted samples. Predictions and teacher corrections do not automatically become training data.

## AI pipeline
Validate upload/type/size → quality checks → detect faces → filter detections → generate embeddings using approved model → compare against eligible roster → aggregate across photos → deduplicate → detect ambiguity → return structured candidates → record model/config versions → send to teacher review.

Candidate states: PRESENT candidate, REVIEW, UNKNOWN, OTHER CLASS, LOW QUALITY, NOT DETECTED. None of the uncertain states automatically means ABSENT.

Evaluate model license, institutional-use rights, false-match and false-non-match rates. Pin model versions. Never claim 100% accuracy. Keep AI service/worker isolated with bounded retries, timeouts and manual fallback. Keep biometric data private.
