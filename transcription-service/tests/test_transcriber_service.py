from unittest.mock import Mock

import numpy as np
import pytest
from transcription_service.errors.model_not_loaded_error import ModelNotLoadedError
from transcription_service.services.transcriber_service import TranscriberService
from transcription_service.settings import Settings

AUDIO = np.zeros(1, dtype=np.float32)


@pytest.fixture
def model_mock():
    model = Mock()
    model.transcribe.return_value = {
        "language": "it",
        "segments": [
            {"text": "this is a mocked text", "start": 0.0, "end": 3.0},
            {"text": "   ", "start": 3.0, "end": 3.5},
            {"text": "42", "start": 3.5, "end": 4.0},
            {"text": "@#!\"'", "start": 4, "end": 4.5},
        ],
    }
    return model


@pytest.fixture
def transcriber_service(settings, model_mock, monkeypatch):
    monkeypatch.setattr("whisperx.load_model", lambda *args, **kwargs: model_mock)
    monkeypatch.setattr("whisperx.load_audio", lambda path: AUDIO)

    service = TranscriberService(settings)

    return service


def test__is_ready__returns_false__if_model_is_not_loaded(settings, transcriber_service):
    assert transcriber_service.is_ready() is False


def test__load__passes_args_to_load_model(settings, monkeypatch):
    load_model = Mock(return_value=Mock())
    monkeypatch.setattr("whisperx.load_model", load_model)

    TranscriberService(settings).load()

    load_model.assert_called_once_with(
        settings.model,
        settings.device,
        compute_type=settings.compute_type,
        download_root=str(settings.models_dir / "whisperx"),
    )


def test__load__makes_service_ready(transcriber_service):
    transcriber_service.load()
    assert transcriber_service.is_ready() is True


def test__load__creates_models_dir(monkeypatch, tmp_path):
    settings = Settings(_env_file=None, models_dir=tmp_path / "models" / "whisperx")
    monkeypatch.setattr("whisperx.load_model", lambda *args, **kwargs: Mock())

    TranscriberService(settings).load()

    assert settings.models_dir.is_dir()


def test__load__is_idempotent(settings, monkeypatch):
    load_model = Mock(return_value=Mock())
    monkeypatch.setattr("whisperx.load_model", load_model)

    service = TranscriberService(settings)
    service.load()
    service.load()

    load_model.assert_called_once()


def test__transcribe__if_not_loaded__raise_model_not_loaded_error(settings, tmp_path):
    service = TranscriberService(settings)

    with pytest.raises(ModelNotLoadedError):
        service.transcribe(tmp_path / "a.mp3", None)


def test__transcribe__joins_segments_and_skips_empty(transcriber_service, tmp_path):
    transcriber_service.load()

    result = transcriber_service.transcribe(tmp_path / "a.mp3", "en")

    assert result.text == "this is a mocked text 42 @#!\"'"


def test__transcribe__passes_correct_args_to_model_transcribe(
    transcriber_service, model_mock, settings, tmp_path
):
    transcriber_service.load()

    transcriber_service.transcribe(tmp_path / "a.mp3", "en")

    model_mock.transcribe.assert_called_once_with(
        AUDIO, language="en", batch_size=settings.batch_size
    )


def test__transcribe__returns_empty_text__with_no_segments(
    transcriber_service, model_mock, tmp_path
):
    transcriber_service.load()
    model_mock.transcribe.return_value = {"language": "en", "segments": []}

    result = transcriber_service.transcribe(tmp_path / "a.mp3", None)

    assert result.text == ""
