# Local XTTS server

Install Python 3.10+, `ffmpeg`, and the server dependencies (`coqui-tts` does not pull in PyTorch, needs its `codec` extra with PyTorch 2.9+, and does not yet work with `transformers` 5):

```bash
brew install ffmpeg
python3 -m venv .venv
source .venv/bin/activate
pip install 'coqui-tts[codec]' 'transformers>=4.57,<5' torch torchaudio fastapi uvicorn numpy
```

Run the server (the XTTS-v2 model downloads on first launch):

```bash
python server.py --host 0.0.0.0 --port 8020
```

Find the Mac's Wi-Fi IP in **System Settings → Wi-Fi → Details**, or run `ipconfig getifaddr en0`. If macOS asks, allow incoming connections for Python; otherwise allow it under **System Settings → Network → Firewall → Options**.

Connect the Android device to the same Wi-Fi network. In the app settings, choose **Local XTTS server**, enter `http://MAC_IP:8020` as the Server URL, and use **Test connection**. If `XTTS_TOKEN` is set when starting the server, enter the same value in the Token field.

If the Wi-Fi network isolates clients (the phone cannot reach the Mac), connect the phone over USB instead, run `adb reverse tcp:8020 tcp:8020`, and use `http://127.0.0.1:8020` as the Server URL.
