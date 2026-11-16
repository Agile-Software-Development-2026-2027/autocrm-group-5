import pytest
from fastapi import HTTPException, status
from fastapi.routing import APIRoute
from transcription_service.dependencies import require_api_key
from transcription_service.routers.transcriber_router import transcriber_router

from tests.conftest import API_KEY


def depends_on(dependant, target) -> bool:
    return any(
        dep.call is target or depends_on(dep, target) for dep in dependant.dependencies
    )


def test__all_transcriber_routes__have_require_api_key():
    routes = [r for r in transcriber_router.routes if isinstance(r, APIRoute)]
    assert routes
    missing = [r.path for r in routes if not depends_on(r.dependant, require_api_key)]
    assert not missing, f"Route without require_api_key: {missing}"


def test__require_api_key__returns_none_when_correct(request_mock):
    assert require_api_key(request_mock, API_KEY) is None


def test__require_api_key__raises_401_when_wrong(request_mock):
    with pytest.raises(HTTPException) as e:
        require_api_key(request_mock, "wrong key")

    assert e.value.status_code == status.HTTP_401_UNAUTHORIZED
    assert e.value.detail == "Invalid or missing API key"


def test__require_api_key__raises_401_when_missing(request_mock):
    with pytest.raises(HTTPException) as e:
        require_api_key(request_mock, None)

    assert e.value.status_code == status.HTTP_401_UNAUTHORIZED
    assert e.value.detail == "Invalid or missing API key"
