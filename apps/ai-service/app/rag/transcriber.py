import logging
import subprocess
import tempfile
from pathlib import Path

from app.config.settings import settings

logger = logging.getLogger(__name__)


class VideoTranscriber:
    def __init__(self) -> None:
        if not settings.groq_api_key:
            logger.warning(
                "GROQ_API_KEY is not set — video lessons will not be transcribed"
            )
        else:
            logger.info(
                "VideoTranscriber ready — model=%s", settings.groq_whisper_model
            )
        self._api_key = settings.groq_api_key

    def transcribe(self, video_bytes: bytes, content_key: str) -> str:
        if not self._api_key:
            logger.warning("Skipping transcription — GROQ_API_KEY not set")
            return ""

        suffix = Path(content_key).suffix or ".mp4"

        logger.info("Writing video to temp file (%d bytes, suffix=%s)", len(video_bytes), suffix)
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as vf:
            vf.write(video_bytes)
            video_path = vf.name

        audio_path = video_path + ".mp3"
        try:
            logger.info("Running ffmpeg: %s -> %s (16kHz mono mp3)", video_path, audio_path)
            result = subprocess.run(
                [
                    "ffmpeg", "-y",
                    "-i", video_path,
                    "-vn",
                    "-ar", "16000",
                    "-ac", "1",
                    "-f", "mp3",
                    audio_path,
                ],
                capture_output=True,
                timeout=300,
            )

            if result.returncode != 0:
                logger.error(
                    "ffmpeg failed (returncode=%d) for key=%s\nstderr: %s",
                    result.returncode,
                    content_key,
                    result.stderr.decode(errors="ignore"),
                )
                return ""

            audio_size = Path(audio_path).stat().st_size
            logger.info("ffmpeg succeeded — audio file size=%d bytes", audio_size)

            logger.info(
                "Sending audio to Groq Whisper API model=%s", settings.groq_whisper_model
            )
            from groq import Groq

            client = Groq(api_key=self._api_key)
            with open(audio_path, "rb") as af:
                transcription = client.audio.transcriptions.create(
                    model=settings.groq_whisper_model,
                    file=af,
                )

            chars = len(transcription.text)
            logger.info(
                "Groq Whisper transcription complete — key=%s chars=%d preview=%r",
                content_key,
                chars,
                transcription.text[:120],
            )
            return transcription.text

        finally:
            Path(video_path).unlink(missing_ok=True)
            Path(audio_path).unlink(missing_ok=True)
            logger.info("Cleaned up temp files")
