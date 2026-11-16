class ModelNotLoadedError(RuntimeError):
    """Raised when a transcription is requested before WhisperX is loaded."""

    def __init__(self, message: str = "WhisperX model is not loaded") -> None:
        super().__init__(message)
