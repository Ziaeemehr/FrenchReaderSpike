import argparse
import base64
import io
import os
import re
import subprocess
import threading
import wave

import numpy as np
import uvicorn
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel

os.environ.setdefault("COQUI_TOS_AGREED", "1")

from TTS.api import TTS

SAMPLE_RATE = 24_000
SILENCE_MS = 150
MODEL_NAME = "tts_models/multilingual/multi-dataset/xtts_v2"
ABBREVIATIONS = ("M.", "Mme.", "Mme", "Dr.")

app = FastAPI()
tts = TTS(MODEL_NAME).to("cpu")
synthesis_lock = threading.Lock()


class SynthesisRequest(BaseModel):
    text: str
    speaker: str
    rate_percent: int
    language: str = "fr"


def require_token(x_token: str | None = Header(default=None)):
    expected = os.environ.get("XTTS_TOKEN")
    if expected and x_token != expected:
        raise HTTPException(status_code=401, detail="Invalid or missing X-Token")


def split_sentences(text: str) -> list[str]:
    protected = text.strip()
    replacements = {}
    for index, abbreviation in enumerate(ABBREVIATIONS):
        marker = f"__ABBR_{index}__"
        replacements[marker] = abbreviation
        protected = protected.replace(abbreviation, marker)
    sentences = re.split(r"(?<=[.!?…])\s+", protected)
    merged: list[str] = []
    for sentence in sentences:
        sentence = restore_abbreviations(sentence.strip(), replacements)
        if not sentence:
            continue
        if merged and not re.search(r"\w", sentence):
            merged[-1] = f"{merged[-1]} {sentence}"
        else:
            merged.append(sentence)
    return merged


def clean_for_tts(text: str) -> str:
    """Drop quotes, brackets and symbols XTTS vocalizes as noise; keep sentence punctuation."""
    text = re.sub(r"[«»“”„‟\"‹›\[\]{}()<>*_#~^|\\/=+@]", " ", text)
    text = re.sub(r"\s*[—–]+\s*", ", ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def restore_abbreviations(text: str, replacements: dict[str, str]) -> str:
    for marker, abbreviation in replacements.items():
        text = text.replace(marker, abbreviation)
    return text


def to_pcm16(audio) -> np.ndarray:
    samples = np.asarray(audio, dtype=np.float32).reshape(-1)
    return (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)


def encode_mp3(samples: np.ndarray) -> bytes:
    wav_buffer = io.BytesIO()
    with wave.open(wav_buffer, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(SAMPLE_RATE)
        wav_file.writeframes(samples.tobytes())
    process = subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", "pipe:0", "-f", "mp3", "pipe:1"],
        input=wav_buffer.getvalue(),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if process.returncode != 0:
        raise RuntimeError(f"ffmpeg failed: {process.stderr.decode(errors='replace').strip()}")
    return process.stdout


@app.get("/health", dependencies=[Depends(require_token)])
def health():
    return {"ok": True}


@app.get("/speakers", dependencies=[Depends(require_token)])
def speakers():
    return list(tts.synthesizer.tts_model.speaker_manager.speakers.keys())


@app.post("/synthesize", dependencies=[Depends(require_token)])
def synthesize(request: SynthesisRequest):
    sentences = split_sentences(request.text)
    if not sentences:
        raise HTTPException(status_code=400, detail="Text is empty")

    silence = np.zeros(SAMPLE_RATE * SILENCE_MS // 1000, dtype=np.int16)
    parts = []
    boundaries = []
    sample_offset = 0
    speed = 1 + request.rate_percent / 100
    if speed <= 0:
        raise HTTPException(status_code=400, detail="rate_percent must be greater than -100")

    with synthesis_lock:
        for index, sentence in enumerate(sentences):
            tts_text = clean_for_tts(sentence)
            if not re.search(r"\w", tts_text):
                tts_text = "."
            samples = to_pcm16(
                tts.tts(text=tts_text, speaker=request.speaker, language=request.language, speed=speed)
            )
            boundaries.append(
                {
                    "text": sentence,
                    "offset_ms": sample_offset * 1000 / SAMPLE_RATE,
                    "duration_ms": len(samples) * 1000 / SAMPLE_RATE,
                }
            )
            parts.append(samples)
            sample_offset += len(samples)
            if index < len(sentences) - 1:
                parts.append(silence)
                sample_offset += len(silence)

        audio_b64 = base64.b64encode(encode_mp3(np.concatenate(parts))).decode("ascii")
    return {"sentences": boundaries, "audio_b64": audio_b64}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Local XTTS-v2 server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8020)
    args = parser.parse_args()
    uvicorn.run(app, host=args.host, port=args.port)
