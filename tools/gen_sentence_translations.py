#!/usr/bin/env python3
"""Generate bundled Persian example translations; Python 3 standard library only.

Each successful batch is atomically persisted. stMeta.fa records source/model/prompt
identity per row so interrupted runs and changed sentences resume safely.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import tempfile
import urllib.error
import urllib.request

PROMPT_REVISION = "lines-fa-v1"
# Exact Persian sentence prompt: app/src/main/java/com/ziaee/frenchreader/
# translate/llm/LlmTranslator.kt, linesPrompt(targets, null, null, "fa").
SYSTEM_PROMPT = (
    "You translate French into natural, fluent Persian (Farsi) for a learner of French. "
    "Translate only the TARGETS. Use the context only to understand them. Subtitles of speech can be "
    "fragments or contain transcription mistakes: translate what the speaker means, keeping a fragment a fragment. "
    "Use standard Persian spelling with half-spaces (ZWNJ). "
    "If a target is already in Persian (Farsi), return it unchanged. "
    'Reply with JSON only: {"translations": [ ... ]}, with exactly one string per target, in the same order.'
)


def provenance(sentence, model):
    return {"source": hashlib.sha256(sentence.encode("utf-8")).hexdigest(),
            "model": model, "promptRevision": PROMPT_REVISION}


def current(row, model):
    value = row.get("st", {}).get("fa")
    meta = row.get("stMeta", {}).get("fa", {})
    expected = provenance(row["s"], model)
    return (isinstance(value, str) and bool(value.strip())
            and (meta == expected or
                 (meta.get("reviewed") and meta.get("source") == expected["source"])))


def request_with_timeout_retry(sentences, args):
    """Retry the same batch up to three times on direct or URL-wrapped timeouts."""
    for attempt in range(3):
        try:
            return request_translations(sentences, args)
        except (socket.timeout, urllib.error.URLError) as error:
            if isinstance(error, urllib.error.URLError) and not isinstance(error.reason, socket.timeout):
                raise
            if attempt == 2:
                raise
            print(f"Batch timed out; retrying ({attempt + 1}/2)", flush=True)


def atomic_text(path, text):
    mode = path.stat().st_mode & 0o777
    fd, name = tempfile.mkstemp(prefix="." + path.name, suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as stream:
            stream.write(text)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(name, mode)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def write_rows(path, rows, changed):
    """Replace only changed row spans, retaining surrounding whitespace and other rows."""
    raw = path.read_text(encoding="utf-8")
    decoder = json.JSONDecoder()
    position = raw.index("[") + 1
    edits = []
    for index, row in enumerate(rows):
        while position < len(raw) and (raw[position].isspace() or raw[position] == ','):
            position += 1
        _, end = decoder.raw_decode(raw, position)
        if index in changed:
            original = raw[position:end]
            indent_match = re.search(r'\n([ \t]+)"', original)
            indent = indent_match.group(1) if indent_match else None
            # Current decks are one-line arrays. Multiline rows retain their indentation.
            encoded = json.dumps(row, ensure_ascii=False, indent=indent)
            if indent:
                base = re.search(r'(?:^|\n)([ \t]*)[^\n]*$', raw[:position]).group(1)
                encoded = encoded.replace('\n', '\n' + base)
            edits.append((position, end, encoded))
        position = end
    for start, end, encoded in reversed(edits):
        raw = raw[:start] + encoded + raw[end:]
    atomic_text(path, raw)


def request_translations(sentences, args):
    body = {"model": args.model, "messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": "TARGETS (JSON array): " +
         json.dumps(sentences, ensure_ascii=False, separators=(',', ':'))}],
        "temperature": 0, "response_format": {"type": "json_object"}}
    headers = {"Content-Type": "application/json"}
    if args.api_key_env:
        token = os.environ.get(args.api_key_env)
        if not token:
            raise ValueError("API key environment variable is empty")
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(args.endpoint, json.dumps(body, ensure_ascii=False).encode("utf-8"), headers)
    with urllib.request.urlopen(request, timeout=args.timeout) as response:
        envelope = json.load(response)
    content = envelope["choices"][0]["message"]["content"]
    document = json.loads(content)
    if not isinstance(document, dict):
        raise ValueError("Expected a JSON object")
    values = document.get("translations")
    if (not isinstance(values, list) or len(values) != len(sentences)
            or any(not isinstance(value, str) or not value.strip() for value in values)):
        raise ValueError("Translation count/type/empty-output mismatch")
    return [value.strip() for value in values]


def translate_batch(indices, rows, path, args):
    """Commit smaller batches immediately, even if a later request fails."""
    try:
        values = request_with_timeout_retry([rows[i]["s"] for i in indices], args)
    except (ValueError, KeyError, TypeError, IndexError) as error:
        if len(indices) > 1:
            middle = len(indices) // 2
            translate_batch(indices[:middle], rows, path, args)
            translate_batch(indices[middle:], rows, path, args)
            return
        # A malformed singleton is retried once, then the run stops without losing progress.
        try:
            values = request_with_timeout_retry([rows[indices[0]]["s"]], args)
        except (ValueError, KeyError, TypeError, IndexError) as retry_error:
            raise ValueError(f"Invalid singleton response in {path.name}, row {indices[0]}") from retry_error
    for index, value in zip(indices, values):
        row = rows[index]
        row.setdefault("st", {})["fa"] = value
        row.setdefault("stMeta", {})["fa"] = provenance(row["s"], args.model)
    write_rows(path, rows, set(indices))
    print(f"Saved {len(indices)} sentences in {path.name}", flush=True)


def write_manifest(path, manifest, model, complete):
    manifest["sentenceTranslations"] = {"model": model, "promptRevision": PROMPT_REVISION,
                                        "status": "complete" if complete else "partial"}
    raw = path.read_text(encoding="utf-8")
    match = re.search(r'\n([ \t]+)"', raw)
    atomic_text(path, json.dumps(manifest, ensure_ascii=False, indent=match.group(1) if match else None)
                + ('\n' if raw.endswith('\n') else ''))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=Path(__file__).resolve().parents[1] / "app/src/main/assets/dataset")
    parser.add_argument("--endpoint", default="http://127.0.0.1:8080/v1/chat/completions")
    parser.add_argument("--model", required=True, help="Exact Gemma model identifier served by the endpoint")
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--limit", type=int, help="Maximum number of missing/stale rows to translate")
    parser.add_argument("--dry-run", action="store_true", help="Report pending rows; no requests or writes")
    parser.add_argument("--force", action="store_true", help="Regenerate all sentences, including reviewed/current translations")
    parser.add_argument("--timeout", type=float, default=180)
    parser.add_argument("--api-key-env", help="Optional environment variable containing a server API key")
    args = parser.parse_args()
    if args.batch_size < 1 or args.timeout <= 0 or (args.limit is not None and args.limit < 0):
        parser.error("batch-size/timeout must be positive and limit must be nonnegative")
    manifest_path = args.dataset / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    decks = [(args.dataset / deck["file"]) for deck in manifest["decks"]]
    remaining = args.limit
    total = 0
    for path in decks:
        rows = json.loads(path.read_text(encoding="utf-8"))
        pending = [i for i, row in enumerate(rows) if isinstance(row.get("s"), str)
                   and row["s"].strip() and (args.force or not current(row, args.model))]
        if remaining is not None:
            pending = pending[:remaining]
            remaining -= len(pending)
        total += len(pending)
        if args.dry_run:
            print(f"{path.name}: {len(pending)} pending sentences")
            continue
        for start in range(0, len(pending), args.batch_size):
            translate_batch(pending[start:start + args.batch_size], rows, path, args)
            # Per-row provenance remains authoritative if a process dies before this write.
            write_manifest(manifest_path, manifest, args.model, False)
    if not args.dry_run:
        complete = all(current(row, args.model) for path in decks
                       for row in json.loads(path.read_text(encoding="utf-8"))
                       if isinstance(row.get("s"), str) and row["s"].strip())
        write_manifest(manifest_path, manifest, args.model, complete)
    print(f"{'Would translate' if args.dry_run else 'Translated'} {total} sentences")


if __name__ == "__main__":
    main()
