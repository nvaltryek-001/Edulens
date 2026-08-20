# EduLens Deployment Guide

## 1. Local Deployment (Docker)
Ensure Docker and Docker Compose are installed.
Run:
`docker-compose up -d --build`

## 2. Cloud Deployment (Render/Railway)
1. Fork the repository.
2. Link the repository to your Render/Railway account.
3. Set the environment variables: `JWT_SECRET`, `FIREBASE_CREDENTIALS`.
4. Deploy the Python web service using the provided Dockerfile.
