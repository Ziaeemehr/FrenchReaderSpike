"""
Chaquopy entry point for the TTS technical spike.

Called from Kotlin as:
    Python.getInstance().getModule("tts_engine")
        .callAttr("synthesize", text, voice, outPath).toString()

Returns a JSON string: a list of {"text", "offset_ms", "duration_ms"} word
boundary events, in the order edge-tts emitted them. The caller writes the
raw MP3 bytes to `out_path` as a side effect.

This mirrors the same edge_tts.Communicate(...).stream() pattern already
used successfully in Ziaee's ankideck project (add_tts.py / tts.py) -- the
only new part is running it from inside Chaquopy on Android instead of on
a desktop.
"""
import asyncio
import json

import edge_tts


def synthesize(text: str, voice: str, out_path: str) -> str:
    """Synchronous entry point Kotlin calls (Chaquopy calls are blocking,
    so this must not be called from the main thread -- do it from a
    coroutine / background thread in Kotlin)."""
    return asyncio.run(_synthesize_async(text, voice, out_path))


async def _synthesize_async(text: str, voice: str, out_path: str) -> str:
    communicate = edge_tts.Communicate(text, voice, boundary="WordBoundary")
    boundaries = []

    with open(out_path, "wb") as f:
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                f.write(chunk["data"])
            elif chunk["type"] == "WordBoundary":
                boundaries.append(
                    {
                        "text": chunk["text"],
                        # edge-tts reports offset/duration in 100ns units
                        "offset_ms": chunk["offset"] / 10000,
                        "duration_ms": chunk["duration"] / 10000,
                    }
                )

    if not boundaries:
        raise RuntimeError(
            "No WordBoundary events received -- edge-tts call likely failed "
            "silently or the service rejected the request."
        )

    return json.dumps(boundaries, ensure_ascii=False)


def synthesize_sentences(text: str, voice: str, rate: str, out_path: str) -> str:
    """Main entry point for the real app (not the spike screen).

    Uses SentenceBoundary events instead of WordBoundary: the design doc
    wants the *sentence* background to change with real playback time, not
    per-word highlighting, and edge-tts's own sentence segmentation already
    handles French abbreviations/elisions/quotes correctly (proven by the
    word-level spike) so we don't need to re-implement sentence splitting
    in Kotlin/Python -- we just trust edge-tts's own boundaries and render
    them in order.

    `rate` is edge-tts's rate string, e.g. "+0%", "-15%", "+20%".

    Returns JSON: {"sentences": [{"text","offset_ms","duration_ms"}, ...]}
    """
    return asyncio.run(_synthesize_sentences_async(text, voice, rate, out_path))


async def _synthesize_sentences_async(text: str, voice: str, rate: str, out_path: str) -> str:
    communicate = edge_tts.Communicate(text, voice, rate=rate, boundary="SentenceBoundary")
    sentences = []

    with open(out_path, "wb") as f:
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                f.write(chunk["data"])
            elif chunk["type"] == "SentenceBoundary":
                sentences.append(
                    {
                        "text": chunk["text"],
                        "offset_ms": chunk["offset"] / 10000,
                        "duration_ms": chunk["duration"] / 10000,
                    }
                )

    if not sentences:
        raise RuntimeError(
            "No SentenceBoundary events received -- edge-tts call likely "
            "failed silently, text was empty after cleanup, or the service "
            "rejected the request."
        )

    return json.dumps({"sentences": sentences}, ensure_ascii=False)


def list_voices_sync() -> str:
    """Optional helper: confirm French voices are reachable from the device.
    Not used by the spike screen by default, but handy to call manually
    while debugging in the Chaquopy console / logcat."""
    return asyncio.run(_list_voices_async())


async def _list_voices_async() -> str:
    voices = await edge_tts.list_voices()
    fr_voices = [v["ShortName"] for v in voices if v["ShortName"].startswith("fr-FR")]
    return json.dumps(fr_voices, ensure_ascii=False)
