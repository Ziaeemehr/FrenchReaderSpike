# French Reader: language model setup for your AI assistant

You are helping a user install a local language model for French Reader. Ask before running commands or installing software. Go step by step, explain each action in plain language, and verify each step before continuing. If you cannot run commands on their computer, give one command at a time and ask for its output. Never ask them to post secrets. Adapt commands to their OS and shell; do not run every OS variant.

## 1. Identify the computer and choose a model

Ask which OS they use, then verify OS, architecture, total memory and available memory:

macOS (Terminal):
```sh
sw_vers
uname -m
sysctl -n hw.memsize
vm_stat
```
`hw.memsize` is bytes; divide by 1,073,741,824 for GiB. `vm_stat` reports memory pages and the page size.

Windows (PowerShell):
```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption, OSArchitecture, TotalVisibleMemorySize, FreePhysicalMemory
Get-CimInstance Win32_ComputerSystem | Select-Object TotalPhysicalMemory
```
The OS memory fields are KiB; `TotalPhysicalMemory` is bytes.

Linux:
```sh
uname -srm
free -h
```

The project's example is `ggml-org/gemma-4-26B-A4B-it-GGUF:Q4_0`; the project README estimates about 16 GB of memory for it. This is a model memory estimate, not a guarantee that a 16 GB computer can comfortably run it alongside the OS and other programs.

Choose based on available memory, not just installed RAM. With substantially more than 16 GB available, offer the project example. With 16 GB or less available, choose a smaller quantized instruction/chat GGUF that supports French translation, leaving memory for the OS and context. With very little free memory, close other applications or explain the limitation before downloading. The project does not prescribe a smaller model or fixed RAM-to-parameter table: verify a candidate's actual GGUF size, model card and llama.cpp compatibility before naming or downloading it. Explain download size and get permission. Do not pretend parameter count alone predicts memory usage.

## 2. Install and verify llama.cpp

macOS, if Homebrew is already installed:
```sh
brew install llama.cpp
llama-server --help
```

If Homebrew is missing, explain its installation and obtain permission before following its official instructions. On Windows/Linux, download a matching build from [llama.cpp releases](https://github.com/ggml-org/llama.cpp/releases) for the detected OS/CPU/GPU, extract it to a user-owned directory, and open a terminal there. Do not invent a release filename. Use `./llama-server` on Linux or `.\llama-server.exe` in PowerShell if it is not on PATH. Verify with `--help`. Check that the installed build supports the options below; update it if it cannot load the example model or recognize an option.

Start the project example in a dedicated terminal, only if it fits the computer:
```sh
llama-server -hf ggml-org/gemma-4-26B-A4B-it-GGUF:Q4_0 --alias gemma4 --port 8080 --reasoning-budget 0
```

Keep this listening on **127.0.0.1:8080**, not the network. The project's command relies on llama-server's loopback default: check its startup log and installed `--help` for the binding. If the environment overrides that default, use the installed server's documented host setting to restore loopback before proceeding. Do not add host flags to `bridge.py`.

For a smaller model, replace the `-hf` repository/quantization with the verified choice; keep port 8080 and the alias consistent. Verify support for `--reasoning-budget 0` with that model/build rather than assuming every model uses reasoning. Downloading/loading may take time; leave this terminal running.

From a second terminal (use `curl.exe` in Windows PowerShell):
```sh
curl --fail http://127.0.0.1:8080/health
curl --fail http://127.0.0.1:8080/v1/models
```
Wait for a successful health response and confirm the model is listed. Test an actual translation on macOS/Linux:
```sh
curl --fail http://127.0.0.1:8080/v1/chat/completions -H 'Content-Type: application/json' -d '{"model":"gemma4","messages":[{"role":"user","content":"Translate Bonjour into English. Reply with the translation only."}]}'
```
PowerShell equivalent (avoids native JSON quoting differences):
```powershell
$body = @{ model = 'gemma4'; messages = @(@{ role = 'user'; content = 'Translate Bonjour into English. Reply with the translation only.' }) } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri http://127.0.0.1:8080/v1/chat/completions -Method Post -ContentType application/json -Body $body
```
Expect a response with a translation in `choices`. Stop and resolve model loading/errors before installing the bridge.

## 3. Download and start the network bridge

The bridge needs **Python 3 only**, no pip packages. Verify `python3 --version` (Windows: `py -3 --version`). If missing, help the user install Python 3 for their OS and recheck. Create a dedicated folder and work there.

macOS/Linux:
```sh
mkdir -p french-reader-server
cd french-reader-server
curl --fail --location --output bridge.py https://raw.githubusercontent.com/Ziaeemehr/FrenchReaderSpike/main/tools/local_server/bridge.py
python3 bridge.py --help
python3 bridge.py
```

Windows PowerShell:
```powershell
New-Item -ItemType Directory -Force french-reader-server
Set-Location french-reader-server
Invoke-WebRequest https://raw.githubusercontent.com/Ziaeemehr/FrenchReaderSpike/main/tools/local_server/bridge.py -OutFile bridge.py
py -3 bridge.py --help
py -3 bridge.py
```

Explain and inspect the downloaded script before running it. `bridge.py` has no host/port/model/token CLI options; `--help` only displays usage. It listens on port **8765** (IPv4/IPv6), forwards `/llm/...` to `127.0.0.1:8080` and `/tts/...` to `127.0.0.1:8020`, and announces `_frenchreader._tcp` over mDNS. Allow incoming connections for Python on the trusted private network if prompted. Do not disable the firewall. The bridge has **no password**: anyone on the same reachable Wi-Fi with this app can use the models. Use it only on a trusted network.

Verify in another terminal:
```sh
curl --fail http://127.0.0.1:8765/frenchreader/info
curl --fail http://127.0.0.1:8765/llm/health
```
Windows: use `curl.exe` for these commands. The info JSON must contain an `id`, `name`, and `services.llm: true`. `services.tts: false` is normal if no voice server is running. The bridge prints its manual LAN address; keep that address for troubleshooting. Leave the model and bridge terminals running.

## 4. Connect the phone

Put the Android phone and computer on the same Wi-Fi. In French Reader: **Settings → Translation → My computer → Find my computer**, then choose the computer. Try translating a text. Pairing remembers the computer even if its IP changes. If it is unavailable, the app falls back to Google Translate; that fallback alone does not prove local translation works. Check bridge requests and model output while testing.

Optional voice: follow [voice-xtts-setup.md](voice-xtts-setup.md). Keep XTTS on `127.0.0.1:8020` for the bridge, then enable **Use my paired computer** in the voice settings. If XTTS uses `XTTS_TOKEN`, set the same environment variable in the bridge's terminal before starting it.

## 5. Troubleshoot one layer at a time

- Local model health fails: read its terminal log, check memory and that port 8080 is free. Wait for loading; use a smaller verified model if it runs out of memory.
- Bridge info fails locally: inspect its log; check Python 3, port 8765 and IPv6 support. Do not invent bridge flags to change the binding.
- Discovery fails but local checks pass: macOS includes Bonjour (`dns-sd`). On Debian/Ubuntu Linux install `avahi-utils` (with permission: `sudo apt install avahi-utils`) and ensure the Avahi service is running. On Windows install Bonjour and verify `dns-sd` is available on PATH; restart the bridge after installation. The script requires `dns-sd` or `avahi-publish` to advertise. Without either, enable **Manual address (USB)** in **Settings → Translation → My computer** and enter `http://COMPUTER_IP:8765/llm`, replacing COMPUTER_IP with the computer’s LAN IP printed by the bridge. Keep the `/llm` prefix for translation requests.
- From the phone browser open `http://COMPUTER_IP:8765/frenchreader/info`, replacing COMPUTER_IP with the bridge's printed address. If this fails while local checks pass, check the Python firewall rule and trusted LAN connectivity (TCP 8765; mDNS discovery uses UDP 5353).
- Guest/public Wi-Fi and some routers isolate clients. Connect the computer to the phone's hotspot and retest, or use USB.
- USB fallback needs Android platform-tools, USB debugging and the phone's authorization. After permission, verify the device and reverse the model port:
```sh
adb devices
adb reverse tcp:8080 tcp:8080
adb reverse --list
```
In **Settings → Translation → My computer**, turn on **Manual address (USB)** and use `http://127.0.0.1:8080`. This connects directly to llama-server, bypassing bridge discovery. Reapply reverse after reconnecting if necessary.
- Stop the servers with Ctrl+C when no longer needed. To use them again, restart llama-server and the bridge; do not reinstall everything.

## Sources

Project behavior comes from [local server README](https://github.com/Ziaeemehr/FrenchReaderSpike/blob/main/tools/local_server/README.md), [bridge.py](https://github.com/Ziaeemehr/FrenchReaderSpike/blob/main/tools/local_server/bridge.py), and the app's Settings and `computer_guide_*` strings. Check installed llama-server help when adapting the command; its [upstream server documentation](https://github.com/ggml-org/llama.cpp/tree/master/tools/server) describes the HTTP API. Model availability, hardware fit and OS installation prerequisites must be verified on the user's computer.
