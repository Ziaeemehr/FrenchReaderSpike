# Use your computer's models from the phone

French Reader can translate with a language model and read texts aloud with an XTTS voice running on your computer. `bridge.py` connects the two over your Wi-Fi without typing an IP address.

- The model servers keep listening on the computer only: other tools that use them (for example a browser extension) need nothing new.
- The bridge is the only part reachable from the network. It asks for a 6-digit pairing code and announces the computer on the local network (Bonjour / mDNS), so the app finds it by itself, even when the computer's address changes.
- It needs only Python 3, no extra packages.

## 1. Start a language model

Install [llama.cpp](https://github.com/ggml-org/llama.cpp) (`brew install llama.cpp` on macOS) and start an OpenAI-compatible server on port 8080, for example:

```
llama-server -hf ggml-org/gemma-4-26B-A4B-it-GGUF:Q4_0 --alias gemma4 --port 8080 --reasoning-budget 0
```

This model needs about 16 GB of memory; a smaller model also works on smaller computers.

Optional: start the XTTS voice server on port 8020 (see [`../xtts_server`](../xtts_server/README.md)), with `--host 127.0.0.1`.

## 2. Start the bridge

```
python3 bridge.py
```

It prints the pairing code. If the computer asks, allow incoming connections for Python.

- `python3 bridge.py --code` prints the code again.
- `python3 bridge.py --new-code` makes a new code; paired phones then ask for it once more.

## 3. Pair the phone

In the app: **Settings → Translation → My computer → Find my computer**, choose your computer and enter the code. For the voice, turn on **Use my paired computer** in the voice server settings.

That is all: whenever the computer, the bridge and the phone are on the same Wi-Fi, the app uses the computer; otherwise it quietly falls back to Google Translate.

## If the computer is not found

Finding the computer uses Bonjour (mDNS): built into macOS; on Linux install `avahi-utils`, on Windows install Bonjour. Without it the bridge still works with the manual address it prints.

If the voice server was started with `XTTS_TOKEN`, start the bridge with the same variable set.

Some networks (public Wi-Fi, some routers) stop devices on the same Wi-Fi from seeing each other. Then either:

- connect the computer to the phone's hotspot, or
- connect the phone by USB, run `adb reverse tcp:8080 tcp:8080`, and use the manual address `http://127.0.0.1:8080` (turn on **Manual address (USB)**).
