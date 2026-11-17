import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from transcription_service.routers.transcriber_router import transcriber_router
from transcription_service.services.transcriber_service import TranscriberService
from transcription_service.settings import Settings

from .routers.health_router import health_router

logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s %(levelname)-8s [%(name)s] %(message)s",
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = Settings()

    transcriber_service = TranscriberService(settings)
    transcriber_service.load()

    app.state.settings = settings
    app.state.transcriber_service = transcriber_service

    yield

    app.state.settings = None
    app.state.transcriber_service = None


app = FastAPI(lifespan=lifespan)

app.include_router(health_router)
app.include_router(transcriber_router)
