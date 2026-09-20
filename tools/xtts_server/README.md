# Local XTTS server

Install Python 3, `ffmpeg`, and the server dependencies:

```bash
brew install ffmpeg
python3 -m venv .venv
source .venv/bin/activate
pip install coqui-tts fastapi uvicorn numpy
```

Run the server (the XTTS-v2 model downloads on first launch):

```bash
python server.py --host 0.0.0.0 --port 8020
```

Find the Mac's Wi-Fi IP in **System Settings → Wi-Fi → Details**, or run `ipconfig getifaddr en0`. If macOS asks, allow incoming connections for Python; otherwise allow it under **System Settings → Network → Firewall → Options**.

Connect the Android device to the same Wi-Fi network. In the app settings, choose **Local XTTS server**, enter `http://MAC_IP:8020` as the Server URL, and use **Test connection**. If `XTTS_TOKEN` is set when starting the server, enter the same value in the Token field.
