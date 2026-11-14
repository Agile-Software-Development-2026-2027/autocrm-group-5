import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from transcription_service.routers.health_router import health_router


@pytest.fixture
def client():
    app = FastAPI()
    app.include_router(health_router)
    return TestClient(app)
