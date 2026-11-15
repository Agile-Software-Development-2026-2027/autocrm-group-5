import logging
import shutil
import tempfile
from pathlib import Path
from typing import Annotated

from fastapi import (
    APIRouter,
    Depends,
    File,
    Form,
    HTTPException,
    Request,
    UploadFile,
    status,
)

from transcription_service.dependencies import require_api_key
from transcription_service.errors.model_not_loaded_error import ModelNotLoadedError
from transcription_service.services.transcriber_service import TranscriberService

from ..schemas.transcription import Transcription

BYTES_PER_MIB = 1024**2
logger = logging.getLogger(__name__)
transcriber_router = APIRouter(
    prefix="/api/v1/transcriptions",
    tags=["transcriber"],
    dependencies=[Depends(require_api_key)],
)


@transcriber_router.post(
    path="",
    summary="Create transcription",
    description="Transcribes an uploaded audio file",
    response_model=Transcription,
    status_code=status.HTTP_200_OK,
    responses={
        413: {"description": "Audio file exceeds maximum upload size"},
        500: {"description": "Transcription failed"},
        503: {"description": "WhisperX is not ready"},
    },
)
# why def and not async def?
# https://fastapi.tiangolo.com/async/#very-technical-details
def create_transcription(
    request: Request,
    file: Annotated[UploadFile, File(description="Audio file to transcribe")],
    language: Annotated[
        str | None, Form(pattern=r"^[a-z]{2}$", description="ISO 639-1, es. 'it'")
    ] = None,
) -> Transcription:
    max_size: int = request.app.state.settings.max_upload_mib * BYTES_PER_MIB

    logger.info(
        "Transcription request received: filename=%r, content_type=%s, size=%.2f MB, language=%s",
        file.filename,
        file.content_type,
        file.size / BYTES_PER_MIB,
        language,
    )

    if file.size > max_size:
        logger.warning(
            "Upload rejected: filename=%r size=%.2f MB exceeds limit of %.2f MB",
            file.filename,
            file.size / BYTES_PER_MIB,
            max_size / BYTES_PER_MIB,
        )
        raise HTTPException(
            status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            detail="Audio file too large",
        )

    suffix: str = Path(file.filename or "audio").suffix
    temp_path: Path | None = None

    try:
        with tempfile.NamedTemporaryFile(
            prefix="transcription_", suffix=suffix, delete=False
        ) as temp:
            shutil.copyfileobj(file.file, temp)
            temp_path = Path(temp.name)

            logger.debug("Upload saved to temporary file: %s", temp_path)

            service: TranscriberService = request.app.state.transcriber_service
            transcription = service.transcribe(temp_path, language)

            logger.info("Transcription completed: filename=%r", file.filename)

            return transcription

    except ModelNotLoadedError:
        logger.exception("Transcription failed: filename=%r", file.filename)
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE)
    except Exception as e:
        logger.exception("Transcription failed: filename=%r", file.filename)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Transcription failed",
        ) from e

    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)
            logger.debug("Temporary file removed: %s", temp_path)
