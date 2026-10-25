#!/usr/bin/env python3
"""Network bridge between French Reader on the phone and model servers on this computer.

The language model (llama.cpp's llama-server, port 8080) and the XTTS voice server (port 8020)
keep listening on this computer only, so other local tools that use them need no code. This
bridge is the single thing reachable from the network: it asks for a 6-digit pairing code,
forwards /llm/... and /tts/... to those servers, and announces the computer on the local network
(Bonjour / mDNS) so the app finds it without typing an IP address.

Standard library only. Usage:
    python3 bridge.py              run in the foreground
    python3 bridge.py --code       print the pairing code
    python3 bridge.py --new-code   make a new pairing code (paired phones must enter it again)
"""
from __future__ import annotations

import argparse
import hmac
import json
import os
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PORT = 8765
SERVICE_TYPE = "_frenchreader._tcp"
UPSTREAMS = {"llm": "http://127.0.0.1:8080", "tts": "http://127.0.0.1:8020"}
HEALTH = {"llm": "/health", "tts": "/health"}
CONFIG = Path.home() / ".config" / "frenchreader" / "bridge.json"
# Six digits are easy to type once; each address gets at most 10 wrong codes per minute, for good.
MAX_FAILURES, LOCKOUT_SECONDS = 10, 60
# A voice server started with its own XTTS_TOKEN still works behind the bridge.
UPSTREAM_TOKENS = {"tts": os.environ.get("XTTS_TOKEN")}
MAX_BODY = 16 * 1024 * 1024
HOP_HEADERS = {"connection", "keep-alive", "transfer-encoding", "upgrade", "host", "content-length",
               "authorization", "x-token"}


def load_config(new_code: bool = False) -> dict:
    config = {}
    if CONFIG.exists():
        try:
            config = json.loads(CONFIG.read_text())
        except ValueError:
            config = {}
    changed = False
    if not config.get("id"):
        config["id"], changed = uuid.uuid4().hex[:12], True
    if new_code or not str(config.get("code", "")).isdigit():
        config["code"], changed = f"{secrets.randbelow(10**6):06d}", True
    if changed:
        # Written to a private temporary file and renamed, so a running bridge never reads half a file.
        CONFIG.parent.mkdir(parents=True, exist_ok=True)
        temporary = CONFIG.with_suffix(".tmp")
        temporary.write_text(json.dumps(config))
        os.chmod(temporary, 0o600)
        os.replace(temporary, CONFIG)
    return config


def computer_name() -> str:
    if sys.platform == "darwin":
        try:
            return subprocess.run(["scutil", "--get", "ComputerName"], capture_output=True, text=True,
                                  timeout=5).stdout.strip() or socket.gethostname()
        except OSError:
            pass
    return socket.gethostname()


def upstream_up(service: str) -> bool:
    try:
        with urllib.request.urlopen(UPSTREAMS[service] + HEALTH[service], timeout=2):
            return True
    except urllib.error.HTTPError as error:
        # The voice server answers 401 when it has its own token: it is running.
        return error.code in (401, 403)
    except OSError:
        return False


class Bridge(BaseHTTPRequestHandler):
    config: dict = {}
    config_mtime = 0.0
    name = ""
    failures: dict[str, deque] = {}
    lock = threading.Lock()

    @classmethod
    def current_code(cls) -> str:
        """`bridge.py --new-code` takes effect at once: the old code stops working."""
        try:
            mtime = CONFIG.stat().st_mtime_ns
            if mtime != cls.config_mtime:
                fresh = json.loads(CONFIG.read_text())
                if str(fresh.get("code", "")).isdigit() and fresh.get("id"):
                    cls.config, cls.config_mtime = fresh, mtime
        except (OSError, ValueError):
            pass  # Keep the code in use; never generate one here.
        return str(cls.config["code"])

    def log_message(self, fmt, *args):  # noqa: D401 - quieter, one line per request
        sys.stderr.write(f"{self.address_string()} {fmt % args}\n")

    def _send_json(self, status: int, body: dict) -> None:
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _authorized(self) -> bool:
        client = self.client_address[0]
        now = time.time()
        with self.lock:
            # Sliding window of wrong codes in the last minute; the limit never lapses for good.
            if len(self.failures) > 256:
                for address in [a for a, times in self.failures.items() if not times or now - times[-1] >= LOCKOUT_SECONDS]:
                    del self.failures[address]
            recent = self.failures.setdefault(client, deque())
            while recent and now - recent[0] >= LOCKOUT_SECONDS:
                recent.popleft()
            if len(recent) >= MAX_FAILURES:
                return False
            # Counted before the comparison, so simultaneous guesses cannot all slip under the limit.
            recent.append(now)
        supplied = self.headers.get("X-Token") or ""
        auth = self.headers.get("Authorization") or ""
        if auth.lower().startswith("bearer "):
            supplied = supplied or auth[7:].strip()
        ok = hmac.compare_digest(supplied.encode(), self.current_code().encode())
        if ok:
            with self.lock:
                recent = self.failures.get(client)
                if recent and now in recent:
                    recent.remove(now)
        return ok

    def _read_body(self) -> bytes | None:
        """The whole request body, including a chunked one, so keep-alive stays in sync."""
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            parts = []
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size < 0 or size > MAX_BODY:
                    raise ValueError("chunk size")
                if size == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass  # trailers
                    return b"".join(parts)
                parts.append(self.rfile.read(size))
                self.rfile.readline()
        length = int(self.headers.get("Content-Length") or 0)
        if length < 0 or length > MAX_BODY:
            raise ValueError("content length")
        return self.rfile.read(length) if length else None

    def _handle(self) -> None:
        if self.path.split("?")[0] == "/frenchreader/info":
            # No code needed: the app uses this to recognise the computer before pairing.
            self.current_code()
            self._send_json(200, {"id": self.config["id"], "name": self.name, "version": 1,
                                  "services": {s: upstream_up(s) for s in UPSTREAMS}})
            return
        service, _, rest = self.path.lstrip("/").partition("/")
        if service not in UPSTREAMS:
            self._send_json(404, {"error": {"message": "Unknown path"}})
            return
        if not self._authorized():
            self._send_json(401, {"error": {"message": "Wrong pairing code"}})
            return
        try:
            body = self._read_body()
        except ValueError:
            self.close_connection = True
            self._send_json(400, {"error": {"message": "Malformed request body"}})
            return
        request = urllib.request.Request(f"{UPSTREAMS[service]}/{rest}", data=body, method=self.command)
        for key, value in self.headers.items():
            if key.lower() not in HOP_HEADERS:
                request.add_header(key, value)
        if UPSTREAM_TOKENS.get(service):
            request.add_header("X-Token", UPSTREAM_TOKENS[service])
        try:
            response = urllib.request.urlopen(request, timeout=600)
            status = response.status
        except urllib.error.HTTPError as error:
            response, status = error, error.code
        except OSError:
            self._send_json(503, {"error": {"message": f"The {service} server is not running on the computer"}})
            return
        with response:
            self.send_response(status)
            for key, value in response.headers.items():
                if key.lower() not in HOP_HEADERS:
                    self.send_header(key, value)
            # Streamed so that long speech or slow model replies reach the phone as they come.
            self.send_header("Transfer-Encoding", "chunked")
            self.end_headers()
            # read1 returns what has arrived, so the first spoken sentence is not held back.
            while chunk := response.read1(64 * 1024):
                self.wfile.write(f"{len(chunk):x}\r\n".encode() + chunk + b"\r\n")
                self.wfile.flush()
            self.wfile.write(b"0\r\n\r\n")

    do_GET = do_POST = do_PUT = do_DELETE = _handle
    protocol_version = "HTTP/1.1"


class DualStackServer(ThreadingHTTPServer):
    """Accepts IPv4 and IPv6: the phone may resolve the computer to either address."""
    address_family = socket.AF_INET6
    request_queue_size = 64  # The default 5 refuses bursts, such as a phone opening several requests.

    def server_bind(self):
        self.socket.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 0)
        super().server_bind()


def advertise(name: str, ident: str) -> subprocess.Popen | None:
    """Announce the bridge with Bonjour (macOS) or Avahi (Linux); without either, the IP is typed."""
    if shutil.which("dns-sd"):
        command = ["dns-sd", "-R", name, SERVICE_TYPE, "local", str(PORT), f"id={ident}"]
    elif shutil.which("avahi-publish"):
        command = ["avahi-publish", "-s", name, SERVICE_TYPE, str(PORT), f"id={ident}"]
    else:
        print("No dns-sd or avahi-publish: the app cannot find this computer automatically. "
              "Install Bonjour (Windows) or avahi-utils (Linux), or use the manual address.", file=sys.stderr)
        return None
    return subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def local_addresses() -> list[str]:
    try:
        probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        probe.connect(("192.0.2.1", 9))  # No packet is sent; this only picks the outgoing interface.
        address = probe.getsockname()[0]
        probe.close()
        return [address]
    except OSError:
        return []


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--code", action="store_true", help="print the pairing code and exit")
    parser.add_argument("--new-code", action="store_true", help="make a new pairing code and exit")
    args = parser.parse_args()
    config = load_config(new_code=args.new_code)
    if args.code or args.new_code:
        print(config["code"])
        return
    Bridge.config, Bridge.name = config, computer_name()
    server = DualStackServer(("::", PORT), Bridge)
    announcer = advertise(f"{Bridge.name} (French Reader)", config["id"])

    def stop(*_):
        if announcer:
            announcer.terminate()
        threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    print(f"French Reader bridge on port {PORT} — pairing code {config['code']}", flush=True)
    for address in local_addresses():
        print(f"  manual address: http://{address}:{PORT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
