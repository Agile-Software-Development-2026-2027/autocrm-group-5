from fastapi import status


def test__health__returns_200(client):
    response = client.get("/api/v1/health")
    assert response.status_code == status.HTTP_200_OK