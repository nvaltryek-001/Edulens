import numpy as np
import pickle
from sklearn.ensemble import RandomForestClassifier
import os

os.makedirs('models', exist_ok=True)
model = RandomForestClassifier(n_estimators=10, random_state=42)
X = [np.random.rand(956) for _ in range(10)]
y = ['student_001'] * 5 + ['student_002'] * 5
model.fit(X, y)

with open('models/model.pkl', 'wb') as f:
    pickle.dump(model, f)
with open('models/embeddings.pkl', 'wb') as f:
    pickle.dump({'student_001': X[0], 'student_002': X[5]}, f)

print("Mock models generated successfully.")
