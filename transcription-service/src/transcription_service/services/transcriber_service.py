import logging
import time
from pathlib import Path
from threading import Lock

import whisperx
from whisperx.audio import SAMPLE_RATE

from ..errors.model_not_loaded_error import ModelNotLoadedError
from ..schemas.transcription import Transcription
from ..settings import Settings

logger = logging.getLogger(__name__)


class TranscriberService:
    def __init__(self, settings: Settings) -> None:
        self._settings: Settings = settings
        self.model = None
        self._lock: Lock = Lock()

    def is_ready(self) -> bool:
        return self.model is not None

    def load(self) -> None:
        """Load WhisperX model"""

        if self.is_ready():
            return

        self._settings.models_dir.mkdir(parents=True, exist_ok=True)

        logger.info(
            "Loading WhisperX model: model=%s, device=%s, compute_type=%s, in models_dir=%s",
            self._settings.model,
            self._settings.device,
            self._settings.compute_type,
            self._settings.models_dir,
        )

        t0: float = time.perf_counter()
        try:
            self.model = whisperx.load_model(
                self._settings.model,
                self._settings.device,
                compute_type=self._settings.compute_type,
                download_root=str(self._settings.models_dir / "whisperx"),
            )
        except Exception:
            logger.exception(
                "Failed to load WhisperX model: model=%s", self._settings.model
            )
            raise
        logger.info("WhisperX model loaded in %.2fs", time.perf_counter() - t0)

    def transcribe(self, audio_path: Path, language: str | None) -> Transcription:
        if not self.is_ready():
            logger.error("Transcription requested but WhisperX model is not loaded")
            raise ModelNotLoadedError()

        logger.debug("Waiting for transcription lock")
        with self._lock:
            t0: float = time.perf_counter()
            audio = whisperx.load_audio(str(audio_path))
            logger.info(
                "Audio loaded: path=%s, duration=%s, elapsed=%.2f",
                audio_path,
                len(audio) / SAMPLE_RATE,
                time.perf_counter() - t0,
            )

            t0: float = time.perf_counter()
            result = self.model.transcribe(
                audio, batch_size=self._settings.batch_size, language=language
            )
            detected_language = result.get("language")
            segments: list = result.get("segments", [])
            logger.info(
                "Transcription completed: language=%s (requested=%s), segments=%d, elapsed=%.2fs",
                detected_language,
                language,
                len(segments),
                time.perf_counter() - t0,
            )

            return Transcription(
                text=" ".join(
                    t for segment in segments if (t := segment["text"].strip())
                )
            )
