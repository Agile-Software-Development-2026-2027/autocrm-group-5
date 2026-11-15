from fastapi import APIRouter, Depends, Response, status

from transcription_service.dependencies import require_api_key

health_router = APIRouter(
    prefix="/api/v1", tags=["health"], dependencies=[Depends(require_api_key)]
)


@health_router.get(
    path="/health",
    summary="Checks whether the FastAPI application is running",
    status_code=status.HTTP_200_OK,
    response_class=Response,
)
def health() -> Response:
    return Response(status_code=status.HTTP_200_OK)
