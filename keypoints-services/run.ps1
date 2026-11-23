# Start the keypoints-services microservice (Django) for LOCAL DEVELOPMENT.
#
# Usage (from the keypoints-services folder), ALWAYS the same command:
#     powershell -ExecutionPolicy Bypass -File .\run.ps1
#
# The script is idempotent: the first run sets everything up, the next runs
# only do what is needed (install again only if requirements.txt changed).
#   1. checks that Python 3.12+ is available
#   2. creates the virtual environment .venv (first run only)
#   3. installs the dependencies (first run, or when requirements.txt changes)
#   4. sets a development DJANGO_SECRET_KEY if none is set (never use it in production)
#   5. applies the database migrations (creates/updates the tables from the models),
#      creates the local dev superuser and loads 3 sample transcripts (if missing)
#   6. starts the server on http://127.0.0.1:8701 (Swagger: /swagger/)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
# Port 8701: no other AutoCRM service uses it (frontend 4200, backend 8080, transcription-service 8000).
# Override it with $env:KEYPOINTS_PORT if you need another one.
$Port = if ($env:KEYPOINTS_PORT) { [int]$env:KEYPOINTS_PORT } else { 8701 }

# --- 1. Find a Python 3.12+ interpreter -------------------------------------
function Get-PythonCommand {
    $candidates = @(@('py', '-3.13'), @('py', '-3.12'), @('python'))
    foreach ($candidate in $candidates) {
        $exe = $candidate[0]
        $extraArgs = @($candidate | Select-Object -Skip 1)
        if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) { continue }
        try {
            & $exe @extraArgs -c "import sys; sys.exit(0 if sys.version_info >= (3, 12) else 1)" 2>$null
            if ($LASTEXITCODE -eq 0) { return , $candidate }
        } catch { }
    }
    throw 'Python 3.12 or higher is required (Django 6.1). Install it from https://www.python.org/downloads/'
}

$VenvPython = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'

# --- 2. Create the virtual environment (first run only) ----------------------
if (-not (Test-Path $VenvPython)) {
    $python = Get-PythonCommand
    $exe = $python[0]
    $extraArgs = @($python | Select-Object -Skip 1)
    Write-Host '[setup] Creating the virtual environment .venv ...'
    & $exe @extraArgs -m venv .venv
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the virtual environment.' }
}

# --- 3. Install dependencies (first run, or when requirements.txt changes) ---
$Stamp = Join-Path $PSScriptRoot '.venv\requirements.sha256'
$CurrentHash = (Get-FileHash -Algorithm SHA256 -Path 'requirements.txt').Hash
$InstalledHash = if (Test-Path $Stamp) { (Get-Content $Stamp -Raw).Trim() } else { '' }
if ($CurrentHash -ne $InstalledHash) {
    Write-Host '[setup] Installing dependencies from requirements.txt ...'
    & $VenvPython -m pip install --upgrade pip --quiet
    & $VenvPython -m pip install -r requirements.txt
    if ($LASTEXITCODE -ne 0) { throw 'pip install failed.' }
    Set-Content -Path $Stamp -Value $CurrentHash
} else {
    Write-Host '[setup] Dependencies already up to date.'
}

# --- 4. Development secret key (only if not already set) ---------------------
# A random key for THIS run only: logged-in admin sessions end when you restart.
# In production DJANGO_SECRET_KEY must come from the environment / a secret store.
if (-not $env:DJANGO_SECRET_KEY) {
    $env:DJANGO_SECRET_KEY = & $VenvPython -c "from django.core.management.utils import get_random_secret_key; print(get_random_secret_key())"
    Write-Host '[setup] DJANGO_SECRET_KEY not set: using a random development key for this run.'
}

# --- 5. Database migrations (safe to run every time) -------------------------
& $VenvPython manage.py migrate
if ($LASTEXITCODE -ne 0) { throw 'migrate failed.' }

# --- 5b. Local development superuser (created only if it doesn't exist) ------
# DEV ONLY: weak, well-known credentials for each developer's LOCAL database.
# Never use them on a shared, reachable or production database.
# Override them by setting DJANGO_SUPERUSER_USERNAME / DJANGO_SUPERUSER_PASSWORD before running.
if (-not $env:DJANGO_SUPERUSER_USERNAME) { $env:DJANGO_SUPERUSER_USERNAME = 'agile' }
if (-not $env:DJANGO_SUPERUSER_PASSWORD) { $env:DJANGO_SUPERUSER_PASSWORD = 'agile' }
& $VenvPython manage.py shell -c "from django.contrib.auth import get_user_model; import os, sys; sys.exit(0 if get_user_model().objects.filter(username=os.environ['DJANGO_SUPERUSER_USERNAME']).exists() else 1)" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[setup] Creating the local superuser '$($env:DJANGO_SUPERUSER_USERNAME)' (development only)."
    & $VenvPython manage.py createsuperuser --noinput --email "$($env:DJANGO_SUPERUSER_USERNAME)@example.local"
    if ($LASTEXITCODE -ne 0) { throw 'createsuperuser failed.' }
}

# --- 5c. Sample data: 3 fake transcripts (only on an EMPTY database) ---------
# The data comes from keypoints/fixtures/sample_transcripts.json (versioned in git),
# so every developer starts with the same data. It is loaded only when there are no
# transcripts yet, so your own data is never overwritten. To start again from
# scratch: stop the server, delete db.sqlite3 and run this script again.
& $VenvPython manage.py shell -c "from keypoints.models import Transcript; import sys; sys.exit(0 if Transcript.objects.exists() else 1)" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host '[setup] Empty database: loading the sample transcripts.'
    & $VenvPython manage.py loaddata sample_transcripts
    if ($LASTEXITCODE -ne 0) { throw 'loaddata failed.' }
}

# --- 5d. Fail fast if the port is already taken ------------------------------
$listener = $null
try {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    $listener.Start()
} catch {
    throw "Port $Port is already in use. Stop the other process or set a free port: `$env:KEYPOINTS_PORT = 8702"
} finally {
    if ($listener) { $listener.Stop() }
}

# --- 6. Start the development server -----------------------------------------
Write-Host ''
Write-Host "keypoints-services running on http://127.0.0.1:$Port  (Swagger: http://127.0.0.1:$Port/swagger/)"
Write-Host 'Press CTRL+C to stop.'
& $VenvPython manage.py runserver $Port
