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
