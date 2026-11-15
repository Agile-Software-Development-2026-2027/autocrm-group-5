from pathlib import Path

import pytest
from fastapi import status
from transcription_service.errors.model_not_loaded_error import ModelNotLoadedError
from transcription_service.schemas.transcription import Transcription
from transcription_service.settings import Settings

URL = "/api/v1/transcriptions"
AUDIO = {"file": ("audio.mp3", b"audio mock", "audio/mpeg")}


def test__create_transcription__returns_text(client, transcriber_service_mock):
    transcriber_service_mock.transcribe.return_value = Transcription(
        text="transcription mock"
    )

    response = client.post(URL, files=AUDIO, data={"language": "en"})

    assert response.status_code == status.HTTP_200_OK
    assert response.json()["text"] == "transcription mock"


def test__create_transcription__passes_correct_args_to_service(
    client, transcriber_service_mock
):
    transcriber_service_mock.transcribe.return_value = Transcription(text="")

    client.post(URL, files=AUDIO, data={"language": "en"})

    transcriber_service_mock.transcribe.assert_called_once()
    path, language = transcriber_service_mock.transcribe.call_args.args
    assert isinstance(path, Path)
    assert path.suffix == ".mp3"
    assert language == "en"


def test__create_transcription__language_defaults_is_none(
    client, transcriber_service_mock
):
    transcriber_service_mock.transcribe.return_value = Transcription(text="")

    client.post(URL, files=AUDIO)

    assert transcriber_service_mock.transcribe.call_args.args[1] is None


@pytest.mark.parametrize("language", ["eng", "e", "EN", "3n"])
def test__create_transcription__rejects_invalid_language(
    client, transcriber_service_mock, language
):
    response = client.post(URL, files=AUDIO, data={"language": language})

    assert response.status_code == status.HTTP_422_UNPROCESSABLE_CONTENT
    transcriber_service_mock.transcribe.assert_not_called()


def test__create_transcription__requires_file(client, transcriber_service_mock):
    response = client.post(URL, data={"language": "en"})

    assert response.status_code == status.HTTP_422_UNPROCESSABLE_CONTENT
    transcriber_service_mock.transcribe.assert_not_called()


def test__create_transcription__rejects_file_too_large(
    app, client, transcriber_service_mock, tmp_path
):
    app.state.settings = Settings(_env_file=None, models_dir=tmp_path, max_upload_mib=1)
    audio = b"a" * (1024**2 + 1)

    response = client.post(URL, files={"file": ("audio.mp3", audio, "audio/mpeg")})

    assert response.status_code == status.HTTP_413_CONTENT_TOO_LARGE
    assert response.json()["detail"] == "Audio file too large"
    transcriber_service_mock.transcribe.assert_not_called()


def test__create_transcription__accepts_file_at_the_limit(
    app, client, transcriber_service_mock, tmp_path
):
    app.state.settings = Settings(_env_file=None, models_dir=tmp_path, max_upload_mib=1)
    transcriber_service_mock.transcribe.return_value = Transcription(text="")
    audio = b"a" * 1024**2

    response = client.post(URL, files={"file": ("audio.mp3", audio, "audio/mpeg")})

    assert response.status_code == status.HTTP_200_OK


def test__create_transcription__returns_503_when_model_not_loaded(
    client, transcriber_service_mock
):
    transcriber_service_mock.transcribe.side_effect = ModelNotLoadedError()

    response = client.post(URL, files=AUDIO)

    assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE


def test__create_transcription__returns_500_on_unexpected_error(
    client, transcriber_service_mock
):
    transcriber_service_mock.transcribe.side_effect = RuntimeError()

    response = client.post(URL, files=AUDIO)

    assert response.status_code == status.HTTP_500_INTERNAL_SERVER_ERROR
    assert response.json()["detail"] == "Transcription failed"


def test__create_transcription__removes_temp_file_on_success(
    client, transcriber_service_mock
):
    d = {}

    def fake_transcribe(path, language):
        d["path"] = path
        d["existed_during_call"] = path.exists()
        return Transcription(text="")

    transcriber_service_mock.transcribe.side_effect = fake_transcribe

    client.post(URL, files=AUDIO)

    assert d["existed_during_call"] is True
    assert not d["path"].exists()


def test__create_transcription__removes_temp_file_on_error(
    client, transcriber_service_mock
):
    d = {}

    def fake_transcribe(path, language):
        d["path"] = path
        raise RuntimeError()

    transcriber_service_mock.transcribe.side_effect = fake_transcribe

    client.post(URL, files=AUDIO)

    assert not d["path"].exists()
