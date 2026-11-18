# End-to-End (E2E) Test Report — Audio Transcription to HubSpot CRM

- **Project:** AutoCRM (`autocrm-group-5`)
- **Date:** November 18, 2026
- **Environment:** Windows 11 (Local Development) — Miniconda (`autocrm313`), FastAPI + WhisperX (`transcription-service`, Python), Spring Boot Backend, Web Frontend (Angular), HubSpot CRM
- **Overall Status:** ✅ **PASSED**

---

## 1. Overview & Pipeline Architecture

This document records the environment setup, troubleshooting, execution steps, and final verification of the complete End-to-End (E2E) workflow:

1. **Audio Upload (Frontend → Backend):** User uploads an `.mp3` recording via the web interface (`audio-upload-page.ts`).
2. **Asynchronous Transcription (Backend → `transcription-service`):** The backend queues the job and sends the audio file to WhisperX (`POST /api/v1/transcriptions`).
3. **Polling & Transcript Review (Frontend):** The frontend polls the job status endpoint until completion and displays the editable transcription in the **Transcript Review** view.
4. **CRM Synchronization (Frontend → Backend → HubSpot API):** The user enters the target HubSpot **Deal ID**, approves the text, and publishes it as a **Note** linked to the Deal in HubSpot.

---

## 2. Environment Setup & Troubleshooting (Windows / Conda)

### Issue Encountered
During initial testing, `POST /api/v1/transcriptions` failed with `500 Internal Server Error`:
```text
RuntimeError: Failed to load audio:
```
Noticeably, `stderr` from `ffmpeg` was completely empty.

### Root Cause
Running `ffmpeg -version` inside the active `autocrm313` Conda environment produced blank output and exited silently due to missing/corrupted DLLs in the Conda-bundled binary (`...\miniconda3\envs\autocrm313\Library\bin\ffmpeg.exe`). This broken binary shadowed a working system-wide FFmpeg installation (`Gyan.FFmpeg.Shared` v9.0.2 installed via WinGet) because Conda's `Library\bin` directory took precedence in `PATH`.

### Fix & Reproduction Guide for Team Members (Windows)
If `ffmpeg -version` returns nothing inside `(autocrm313)`:

1. **Install a valid system-wide FFmpeg build** (if not already installed):
   ```powershell
   winget install Gyan.FFmpeg.Shared
   ```
2. **Disable the broken Conda FFmpeg binaries** so the environment falls back to the working WinGet installation:
   ```powershell
   Rename-Item "$env:USERPROFILE\miniconda3\envs\autocrm313\Library\bin\ffmpeg.exe" "ffmpeg.exe.broken"
   Rename-Item "$env:USERPROFILE\miniconda3\envs\autocrm313\Library\bin\ffprobe.exe" "ffprobe.exe.broken" -ErrorAction SilentlyContinue
   ```
3. **Clean up global Windows `PATH` (Recommended):** Remove `...\miniconda3\envs\autocrm313\Library\bin` and `...\miniconda3\condabin` from the user/system `PATH` environment variables if manually added, letting `conda activate autocrm313` manage paths dynamically without DLL conflicts.
4. **Verify FFmpeg & Restart Service:**
   ```powershell
   where.exe ffmpeg
   ffmpeg -version
   ```
   Confirm `ffmpeg -version` prints full build details and exits with code `0`, then restart `transcription-service`.

---

## 3. E2E Test Execution & Verification

| Step | Component / Endpoint | Action Performed | Expected Result | Actual Result & Evidence | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Audio Upload** | Web UI (`audio-upload-page.ts`) → `POST /upload` | Selected and uploaded a test `.mp3` recording. | `202 Accepted` returning a unique `jobId`. | Returned `202 Accepted` with `jobId`: `6d70ea03-b3e1-41be-82f3-40c0647df9c3`. | ✅ Pass |
| **2. Speech-to-Text** | `transcription-service` → `POST /api/v1/transcriptions` | WhisperX loaded audio via FFmpeg and ran transcription inference. | `200 OK` and accurate text transcription. | Returned `200 OK`. Transcribed text: *"Good morning research and development methodologies. This is a testing recording for demonstration purposes."* | ✅ Pass |
| **3. Status Polling & Review** | Frontend Polling (`GET /.../{jobId}`) | Polled job status until `processing` transitioned to completed. | `200 OK` with populated `transcript` field rendered in the review UI. | Transcript loaded cleanly in the **Transcript Review** editable text area. | ✅ Pass |
| **4. HubSpot Submission** | Transcript Review UI → HubSpot Integration | Entered target numeric **Deal ID** (`544546846960`) in *Deal Code* and clicked **Approve and send to HubSpot**. | Note created in HubSpot and associated with Deal `544546846960`. | UI confirmed creation: *"Trascrizione inviata con successo a HubSpot. Nota creata: 548302160089"*. | ✅ Pass |
| **5. CRM Verification** | HubSpot Web Portal | Opened Deal `544546846960` and checked the activity timeline. | Note `548302160089` appears under Deal activities with the exact transcript text. | Confirmed visible under **Previous Activity (Attività precedente) → Note** with matching transcript text. | ✅ Pass |

---

## 4. Known Limitations & Next Sprint Backlog

While the end-to-end pipeline is fully functional, the following improvements were identified during testing and should be prioritized in the next sprint:

1. **Deal Association Only Works via Numeric Deal ID (High Priority)**
   - **Current Behavior:** The **Deal Code** input field only resolves a HubSpot Deal when its exact numeric ID (e.g., `544546846960`) is entered. Entering the human-readable Deal Name fails to associate the note.
   - **Proposed Fix:** Integrate HubSpot's Deal Search API (`POST /crm/v3/objects/deals/search`) so users can search and select deals by **Deal Name** (via autocomplete/dropdown) while still supporting direct numeric Deal ID lookup as a fallback.

2. **Job Polling Stuck on `"processing"` When Transcription Fails (Medium Priority)**
   - **Current Behavior:** If `POST /api/v1/transcriptions` returns a `500 Internal Server Error`, the exception is not propagated to the job store, leaving the job stuck indefinitely in `"status": "processing"` while the frontend keeps polling.
   - **Proposed Fix:** Wrap the transcription service HTTP call in a `try/except` block (checking `response.raise_for_status()`) and update the job state to `"failed"` with an error message so the UI can stop polling and display an actionable alert.
