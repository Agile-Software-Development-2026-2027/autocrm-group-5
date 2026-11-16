# AutoCRM Frontend — Setup and Development Guide

This document explains how to install, run, test, build, and review the Angular frontend of AutoCRM Group 5. The frontend lives in the repository's `frontend/` directory and is separate from the Spring Boot backend.

## 1. Technology and repository structure

- **Frontend:** Angular 22, TypeScript, standalone components, Angular Router, and Vitest.
- **Backend:** Spring Boot 4.1.2 with Java 17, kept in its existing directories at the repository root.
- **CI:** `.github/workflows/frontend-ci.yml` handles frontend tests and builds independently of the existing Java workflow.

Relevant structure:

```text
autocrm-group-5/
├── .github/workflows/
│   ├── ci.yml                  # Existing backend workflow
│   └── frontend-ci.yml         # Frontend workflow
├── docs/
│   └── frontend-setup.md       # This guide
├── frontend/
│   ├── src/app/                # Angular routes, components, services, and tests
│   ├── src/environments/       # Environment-specific frontend configuration
│   ├── package.json
│   ├── package-lock.json
│   └── angular.json
├── src/main/                   # Existing Spring Boot application
├── pom.xml
└── mvnw / mvnw.cmd
```

## 2. Prerequisites

- Node.js and npm compatible with the Angular version specified in `frontend/package.json`. The frontend CI configuration uses **Node.js 26.13.1** and **npm 11.20.0**.
- A terminal opened at the **repository root**.
- For API integration testing only: Java 17 and the existing Spring Boot backend.

Do not commit `node_modules/`, build outputs, local credentials, or environment secrets. Keep `frontend/package-lock.json` versioned to ensure reproducible dependency installation.

## 3. Install and start the frontend

From the repository root:

```bash
cd frontend
npm ci
npm start
```

Open **http://localhost:4200** in a browser. The primary routes are:

- `/audio-upload` — select a file, upload it, and monitor transcription status.
- `/transcript-review` — review and edit the transcription for the current in-memory session.
- `/transcript-review/demo` — local editor demonstration, without live approval actions.

The Review page can show an empty state if it is opened directly without first completing an upload. The current transcription session is kept in memory; refreshing the page may clear it.

## 4. Tests, formatting, and builds

Run all commands from `frontend/`:

```bash
npm run test:ci
npm run build -- --configuration development
npm run build
```

- `npm run test:ci` runs the automated frontend test suite, including mocked HTTP scenarios.
- The development build checks the development configuration.
- `npm run build` checks the production build configuration.

The test suite covers routing, audio-file validation, transcript editing, upload/polling, approval validation, error handling, and Discard behavior. **Approval success and failure are verified with mocked HTTP responses, not with live HubSpot operations.**

Use the formatting checks configured in `frontend/package.json` if needed. Avoid assuming that any particular formatting script exists without checking that file.

## 5. Running against the local Spring Boot backend

The Angular development environment uses **`http://localhost:8080/api`** as the backend API base URL. The frontend dev server runs on port **4200**; Spring Boot runs on port **8080** by default.

To start the existing backend, open another terminal at the repository root:

**Windows PowerShell:**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux/macOS:**

```bash
./mvnw spring-boot:run
```

Use **Java 17** and the backend configuration required by the team. Spring Boot expects the `HUBSPOT_API_TOKEN` environment variable at startup. Obtain any real credentials only through the team's authorized process. Never place them in Angular code, configuration committed to Git, command examples shared publicly, or issue comments.

A valid test of frontend-to-backend communication is an upload that returns **HTTP 202**, followed by status polling until `done` or `failed`. The backend may currently use `StubTranscriptionEngine`: successful HTTP integration does **not** establish that real speech-to-text transcription works.

## 6. Relevant backend endpoints

| Frontend operation | HTTP endpoint | Notes |
| --- | --- | --- |
| Upload audio | `POST /api/transcriptions/upload` | Multipart field: `file`; success returns HTTP 202 and `jobId`. |
| Poll transcription | `GET /api/transcriptions/{jobId}` | Status can be `processing`, `done`, or `failed`; completed responses contain `transcript`. |
| Approve reviewed text | `POST /api/hubspot/sync` | JSON body contains `dealCode` and the user-edited `text`. **May create a real HubSpot note.** |

**API-contract discrepancy:** The existing backend controller exposes `POST /api/hubspot/sync`, while the repository's `docs/api-contract.md` documents `POST /api/transcriptions/{jobId}/approve`. The frontend currently targets the implemented controller. The team should agree on the definitive contract before relying on live CRM approval; this guide does not modify the original API contract.

The audio picker uses the browser's audio MIME information rather than a fixed extension allowlist and enforces a **100 MiB** frontend limit. The backend can independently reject formats it does not support; the frontend displays a readable error in that case.

## 7. Safe manual smoke test

1. Start Angular and open `/transcript-review/demo`. Confirm that the editor can be modified and that no live approval controls appear.
2. Start Spring Boot, visit `/audio-upload`, select a small, non-sensitive audio file, and submit it.
3. In browser Developer Tools → **Network**, verify the upload (`POST`, HTTP 202) and subsequent status requests (`GET`).
4. Confirm that a completed job opens Transcript Review with the server-returned text; edit the text.
5. Verify validation with an **empty Deal Code** or an **empty transcript**, and check that these invalid attempts do not send approval requests.
6. Verify **Discard** clears the local session without sending an HTTP request.

**Do not submit valid approval data to `/api/hubspot/sync` during an ordinary smoke test.** A live approval may write to the team's HubSpot account. Test successful approval, failure responses, and duplicate-submission prevention using mocked HTTP in the automated suite. A live end-to-end CRM test must be separately authorized and use an agreed test Deal.

## 8. CI and delivery

The frontend workflow is defined in `.github/workflows/frontend-ci.yml`. It is intended to run for Pull Requests targeting `main` and pushes to `main`. It installs dependencies with `npm ci`, runs the frontend tests, and builds the Angular application. It does not replace the existing backend Java workflow.

Before merging, check that **both** the frontend and backend GitHub Actions jobs complete successfully and that the Pull Request receives the review required by the team. Local test success is not equivalent to a green GitHub Actions run.

## 9. Known limitations and deployment considerations

- The transcription result and edits are stored in **application memory**; reloading the Review page can lose the current session.
- The transcription engine can return **stub text** pending completion of the actual speech-to-text backend integration.
- Frontend validation and in-memory submission blocking cannot guarantee backend idempotency across reloads, browsers, or repeated requests.
- Approval endpoint documentation and the implemented controller currently differ, as noted above.
- Production hosting, API routing, and any required proxy/CORS setup must be agreed with the team. Do not assume that a development `localhost` URL is valid in production.
- A real HubSpot approval test is **not** covered by the local automated test suite and requires team authorization.

For further details, consult the frontend source/tests and the backend's existing documentation. This guide documents the frontend integration without changing the backend or its original files.
