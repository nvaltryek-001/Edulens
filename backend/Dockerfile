FROM python:3.11-slim

# build tools are needed to compile insightface's Cython extension
RUN apt-get update \
 && apt-get install -y --no-install-recommends build-essential libglib2.0-0 libgomp1 \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

ENV APP_ENV=production \
    EDULENS_DB_PATH=/app/data/edulens.db \
    EDULENS_EMBEDDINGS_PATH=/app/data/embeddings.pkl \
    INSIGHTFACE_HOME=/app/data/models

RUN mkdir -p /app/data /app/uploads /app/dataset

EXPOSE 5000
# 2 workers x 4 threads; the InsightFace model loads once per worker (~1 GB RAM each)
CMD ["gunicorn", "--workers", "2", "--threads", "4", "--timeout", "120", "--bind", "0.0.0.0:5000", "app:app"]
