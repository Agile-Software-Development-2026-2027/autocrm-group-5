# Sprint 2 API Contract (Audio Upload & Review)

This document defines the JSON API endpoints between the frontend and backend.

## 1. Upload Audio
**POST** `/api/transcriptions/upload`
* **Content-Type:** `multipart/form-data`
* **Payload:** `file` (Audio file)
* **Success Response (202 Accepted):**
  ```json
  {
    "jobId": "string",
    "status": "processing"
  }