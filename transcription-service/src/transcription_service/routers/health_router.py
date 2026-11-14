from fastapi import APIRouter, Response, status

health_router = APIRouter(prefix="/api/v1", tags=["health"])


@health_router.get(
    path="/health",
    summary="Checks whether the FastAPI application is running",
    status_code=status.HTTP_200_OK,
    response_class=Response,
)
def health() -> Response:
    return Response(status_code=status.HTTP_200_OK)
