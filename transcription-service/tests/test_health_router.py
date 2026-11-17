from fastapi import status

from tests.conftest import API_KEY, API_KEY_HEADER


def test__health__returns_200(client):
    response = client.get("/api/v1/health", headers={API_KEY_HEADER: API_KEY})
    assert response.status_code == status.HTTP_200_OK