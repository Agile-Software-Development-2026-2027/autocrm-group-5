# keypoints-services

Django REST microservice of AutoCRM: it receives a meeting transcript and returns its key points.

## Prerequisites

- PowerShell
- Python 3.12 or higher. Check with `python --version`

## Run

From the `keypoints-services` folder, paste this in PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

If PowerShell blocks the script ("running scripts is disabled on this system"), enable scripts for your user once:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

Then run the command above again.

## Environment variables

Set them in the shell **before** starting `run.ps1`. Nothing secret is ever committed.

| Variable | Required | Default | Meaning |
|---|---|---|---|
| `LLM_API_KEY` | for extraction | – | Key of the LLM provider. Without it `POST /api/v1/extractions/` answers `502 llm_unavailable`. |
| `LLM_MODEL` | no | `gemini-3.5-flash-lite` | Model used for the extraction. |
| `LLM_BASE_URL` | no | Gemini OpenAI-compatible endpoint | Base URL of the provider. |
| `KEYPOINTS_API_KEY` | no | `api-just-for-test` (local only) | Key the backend must send in `Keypoints-API-Key`. Must equal the backend's `keypoints.service.apiKey`. |
| `DJANGO_SECRET_KEY` | yes | set by `run.ps1` for local dev | Django secret. |

```powershell
$env:LLM_API_KEY = "<your key>"
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

## Tests

The LLM provider is mocked: no real call and no quota used.

```powershell
.\.venv\Scripts\python.exe manage.py test
```
