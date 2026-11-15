from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

PROJECT_ROOT = Path(__file__).resolve().parents[2]  # /transcription-service
ENV_PATH = PROJECT_ROOT / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="TRANSCRIPTION_", env_file=ENV_PATH, frozen=True
    )

    model: str = "small"
    device: str = "cpu"
    compute_type: str = "int8"
    batch_size: int = 4
    max_upload_mib: int = 3072
    models_dir: Path = PROJECT_ROOT / "src" / "transcription_service" / "models"
