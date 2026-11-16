from unittest.mock import create_autospec

import pytest
from fastapi import FastAPI, Request
from fastapi.testclient import TestClient
from transcription_service.routers.health_router import health_router
from transcription_service.routers.transcriber_router import transcriber_router
from transcription_service.services.transcriber_service import TranscriberService
from transcription_service.settings import Settings

API_KEY = "secret key"
API_KEY_HEADER = "Transcription-API-Key"


@pytest.fixture
def settings(tmp_path):
    return Settings(_env_file=None, models_dir=tmp_path, api_key=API_KEY)


@pytest.fixture
def auth_headers():
    return {API_KEY_HEADER: API_KEY}


@pytest.fixture
def transcriber_service_mock():
    mock = create_autospec(TranscriberService, instance=True)
    mock.is_ready.return_value = True
    return mock


@pytest.fixture
def app(settings, transcriber_service_mock):
    app = FastAPI()
    app.include_router(health_router)
    app.include_router(transcriber_router)
    app.state.settings = settings
    app.state.transcriber_service = transcriber_service_mock
    return app


@pytest.fixture
def request_mock(app):
    return Request(scope={"type": "http", "app": app})


@pytest.fixture
def client(app):
    return TestClient(app)
