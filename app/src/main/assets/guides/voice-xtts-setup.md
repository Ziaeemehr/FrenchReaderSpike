# French Reader: XTTS voice setup for your AI assistant

You are helping a user install the local XTTS voice server for French Reader. Ask before running commands, downloading models, installing software or changing firewall settings. Go step by step and verify each step. Explain what the user needs to keep running. If you cannot operate their computer, give one command at a time and ask for the output. Never ask them to share a token in chat.

## 1. Identify the OS and prerequisites

This guide primarily follows the project's macOS setup. Ask which OS and shell the user uses. On macOS verify `sw_vers` and `uname -m`; on Linux use `uname -srm`; on Windows PowerShell use `Get-CimInstance Win32_OperatingSystem | Select-Object Caption, OSArchitecture`.

macOS, with Homebrew already installed:
```sh
brew install uv ffmpeg
uv --version
ffmpeg -version
```
If Homebrew is absent, obtain permission before helping install it from its official instructions.

Windows: install uv from its [official installation instructions](https://docs.astral.sh/uv/getting-started/installation/) (for example `winget install --id=astral-sh.uv -e` if winget is available). Install an ffmpeg build appropriate to Windows and add its executable directory to PATH; reopen PowerShell, then verify `uv --version` and `ffmpeg -version`.

Linux: use the [official uv installation instructions](https://docs.astral.sh/uv/getting-started/installation/) for the detected distribution. Install ffmpeg with the distribution's package manager (Debian/Ubuntu: `sudo apt install ffmpeg`, after permission). Verify `uv --version` and `ffmpeg -version`. Do not assume every Linux distribution uses apt.

## 2. Create a Python 3.11 environment

Choose a user-owned location. The following examples use `xtts-env` in the current folder; an absolute path outside the project also works. Do not reuse an unrelated Python environment.

macOS/Linux:
```sh
uv venv --python 3.11 xtts-env
source xtts-env/bin/activate
python --version
uv pip install 'coqui-tts[codec]' 'transformers>=4.57,<5' torch torchaudio fastapi uvicorn numpy
```

Windows PowerShell:
```powershell
uv venv --python 3.11 xtts-env
.\xtts-env\Scripts\Activate.ps1
python --version
uv pip install 'coqui-tts[codec]' 'transformers>=4.57,<5' torch torchaudio fastapi uvicorn numpy
```
If PowerShell blocks activation, do not change the machine-wide execution policy. Use the environment's Python directly instead:
```powershell
uv pip install --python .\xtts-env\Scripts\python.exe 'coqui-tts[codec]' 'transformers>=4.57,<5' torch torchaudio fastapi uvicorn numpy
.\xtts-env\Scripts\python.exe --version
```
Use that same executable in place of `python` for subsequent Windows commands.

Check dependencies:
```sh
python -c "import sys, torch, torchaudio, transformers, fastapi, uvicorn, numpy; from importlib.metadata import version; print(sys.version); print('coqui-tts', version('coqui-tts')); print('torch', torch.__version__); print('transformers', transformers.__version__)"
```
Confirm Python 3.11, transformers 4.57 or later but below 5, and successful imports. The project notes that coqui-tts does not install PyTorch itself and needs its `codec` extra with PyTorch 2.9+. Do not drop the extra or install transformers 5. GPU-specific PyTorch packages may depend on hardware/driver versions; consult PyTorch's official instructions if the default install cannot use the intended GPU. Do not promise a GPU speed or a fixed RAM requirement.

## 3. Obtain and run the server

With the environment activated, use the project's `tools/xtts_server/server.py`, or download that exact file into a dedicated folder:

macOS/Linux:
```sh
curl --fail --location --output server.py https://raw.githubusercontent.com/Ziaeemehr/FrenchReaderSpike/main/tools/xtts_server/server.py
```
Windows PowerShell:
```powershell
Invoke-WebRequest https://raw.githubusercontent.com/Ziaeemehr/FrenchReaderSpike/main/tools/xtts_server/server.py -OutFile server.py
```
Inspect the downloaded script and explain that the XTTS-v2 model downloads on first launch. The script sets `COQUI_TOS_AGREED=1`; have the user review and accept the model's terms before running it. Keep ffmpeg on PATH.

Choose one connection mode:

**Paired computer / bridge (model server stays on the computer):**
```sh
python server.py --host 127.0.0.1 --port 8020
```

**Direct Wi-Fi URL (phone reaches XTTS itself):**
```sh
python server.py --host 0.0.0.0 --port 8020
```

The server's CLI options are `--host` and `--port`. Token and device settings are environment variables, not command-line flags. It selects CUDA when available, otherwise Apple MPS when available, otherwise CPU. It warms up a first synthesis before printing `XTTS ready on ...`; wait for initialization before testing. Leave this terminal running.

Optional token, set **before** starting the server (choose a private value locally):
```sh
export XTTS_TOKEN='REPLACE_WITH_YOUR_PRIVATE_TOKEN'
python server.py --host 0.0.0.0 --port 8020
```
PowerShell:
```powershell
$env:XTTS_TOKEN = 'REPLACE_WITH_YOUR_PRIVATE_TOKEN'
python server.py --host 0.0.0.0 --port 8020
```
Use `127.0.0.1` instead of `0.0.0.0` for bridge mode. Without a token, direct access is unauthenticated; use a trusted network. Allow incoming Python connections on that network if prompted for direct mode; on macOS check **System Settings → Network → Firewall → Options**. Do not disable the firewall.

## 4. Verify locally before connecting Android

From another terminal, without a token:
```sh
curl --fail http://127.0.0.1:8020/health
curl --fail http://127.0.0.1:8020/speakers
```
Windows: use `curl.exe`. Expect `{"ok":true}` and a nonempty JSON array of speaker names.

If a token is enabled, set the same variable in the test terminal, then:
```sh
curl --fail -H "X-Token: $XTTS_TOKEN" http://127.0.0.1:8020/health
curl --fail -H "X-Token: $XTTS_TOKEN" http://127.0.0.1:8020/speakers
```
PowerShell:
```powershell
Invoke-RestMethod http://127.0.0.1:8020/health -Headers @{'X-Token'=$env:XTTS_TOKEN}
Invoke-RestMethod http://127.0.0.1:8020/speakers -Headers @{'X-Token'=$env:XTTS_TOKEN}
```
A 401 means the token is missing/wrong. All of these endpoints require `X-Token` when `XTTS_TOKEN` is set. A successful health check alone does not verify ffmpeg/audio generation. Test synthesis too; this Python command automatically takes an actual speaker from `/speakers` and verifies nonempty audio:
```sh
python -c "import os,json,urllib.request,base64; h={'Content-Type':'application/json'}; t=os.environ.get('XTTS_TOKEN'); h.update({'X-Token':t} if t else {}); s=json.load(urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8020/speakers',headers=h))); r=urllib.request.Request('http://127.0.0.1:8020/synthesize',data=json.dumps({'text':'Bonjour.','speaker':s[0],'rate_percent':0,'language':'fr'}).encode(),headers=h); result=json.load(urllib.request.urlopen(r,timeout=600)); audio=base64.b64decode(result['audio_b64']); assert audio and result['sentences']; print('Audio bytes:',len(audio))"
```
Run with the activated environment and the token set there if applicable. Resolve errors in the server log before continuing.

## 5. Connect French Reader

### Direct URL

Phone and computer must be on the same Wi-Fi. On macOS find the Wi-Fi IP in **System Settings → Wi-Fi → Details**, or try `ipconfig getifaddr en0` (if that interface has no address, inspect the actual Wi-Fi interface). On Windows use `ipconfig`; on Linux use `ip -brief address`. Choose the LAN address for the shared network, not loopback or a disconnected/VPN interface.

In the app's voice choice, select **Local XTTS server**. Open **Settings → Local voice server (XTTS)**, first turn **Use my paired computer** off, then enter `http://COMPUTER_IP:8020` as **Server URL**, enter the same token in **Token (optional)** if enabled, and use **Test connection**. Choose a speaker and use its listen button, then try reading a French text aloud.

### Through the paired computer

Follow [language-model-setup.md](language-model-setup.md) for downloading/running `bridge.py` and discovery. A language model is optional for voice use: `services.llm: false` is normal if only XTTS is running. Start XTTS with `--host 127.0.0.1 --port 8020`. If using `XTTS_TOKEN`, set the same value in the bridge's environment before starting `python3 bridge.py` (Windows: `py -3 bridge.py`). Restart the bridge if the token changes; it reads it at startup.

Verify (Windows: `curl.exe`):
```sh
curl --fail http://127.0.0.1:8765/frenchreader/info
curl --fail http://127.0.0.1:8765/tts/health
curl --fail http://127.0.0.1:8765/tts/speakers
```
Info should show `services.tts: true`. Info treats even an upstream 401/403 as a running server, so also test `/tts/health` to prove the token forwarding works. The bridge injects `X-Token`; it does not require that token from the phone. Bridge access itself has no password, even if the upstream has a token.

In **Settings → Translation → My computer → Find my computer**, select the computer. In voice server settings enable **Use my paired computer**, then choose a speaker and test. To activate XTTS for the text, open the reader’s menu → **Choose voice → Local XTTS server**, then select a speaker. Discovery requires Bonjour/Avahi as described in the language model guide; the bridge uses port 8765, not 8020.

## 6. Troubleshooting and USB fallback

- First launch is slow: wait for model download and warmup. Inspect the log rather than starting another server on the same port.
- Dependency failure: confirm the active Python is 3.11 and dependencies match step 2. Check ffmpeg with `ffmpeg -version` in the same environment/terminal as the server.
- GPU backend error: after stopping the server, try `export XTTS_DEVICE=cpu` (PowerShell: `$env:XTTS_DEVICE='cpu'`) and restart. CPU can be slower. The script already enables MPS fallback for unsupported operations.
- Local health succeeds but phone fails: check `--host 0.0.0.0` for direct mode, correct IP, Python firewall rule and same Wi-Fi. Guest/public Wi-Fi may isolate devices; connect the computer to the phone's hotspot instead. Bridge mode instead needs port 8765 reachable and Bonjour (`dns-sd`) or Linux `avahi-utils` for discovery.
- USB direct fallback: install Android platform-tools if needed, enable USB debugging with permission, authorize the computer, then:
```sh
adb devices
adb reverse tcp:8020 tcp:8020
adb reverse --list
```
Use `http://127.0.0.1:8020` as the app's **Server URL**, with **Use my paired computer** off for this direct connection. XTTS can bind `127.0.0.1` for USB. Keep the token in the app if configured. Reapply reverse after reconnecting if needed.
- Stop with Ctrl+C. Next time reactivate the environment and start the server (and bridge if used); no reinstall is needed.

## Sources

Server dependencies, options, routes and behavior come from the project's [XTTS README](https://github.com/Ziaeemehr/FrenchReaderSpike/blob/main/tools/xtts_server/README.md), [server.py](https://github.com/Ziaeemehr/FrenchReaderSpike/blob/main/tools/xtts_server/server.py), [bridge.py](https://github.com/Ziaeemehr/FrenchReaderSpike/blob/main/tools/local_server/bridge.py), and the app's voice settings. OS package availability and GPU wheel compatibility vary; verify them on the user's computer rather than guessing.
