# Android on-device llama.cpp quantitative spike

Date: 2026-09-17

Status: completed on the connected physical device. This is standalone throwaway command-line tooling; nothing under `app/` or the Gradle project was changed.

## Executive summary

Qwen3 0.6B Q4_K_M ran successfully with the CPU-only Android arm64 build at both requested context allocations and all requested thread counts. The fastest measured combination was 8 threads at context 2048: **104.58 ± 0.99 prompt tokens/s** and **36.03 ± 1.02 generated tokens/s**. The representative context-2048/4-thread process peaked at **775,312 kB VmRSS/VmHWM (757.14 MiB)**.

## Device and build

- Device: Samsung Galaxy S24 FE (`SM-S721B`), serial `R5CY904AFZZ`
- OS: Android 16 / API 36; kernel `6.1.157-android14-11`
- SoC property: `s5e9945`; 10 online ARM64 CPU cores
- RAM: `7,397,724 kB` from `/proc/meminfo`
- Requested minimum runtime: Android API 26
- Host: macOS 26.6.2, `uname -m` = `x86_64`
- NDK: r30 / `30.0.16248370`, Clang 21.0.0
- NDK source: `https://dl.google.com/android/repository/android-ndk-r30-darwin.zip`
- NDK archive: 974,984,488 bytes; SHA-1 `c060be96767eefbb8e0a27796d6f43115fc1a0c4` (matched Google's repository metadata)
- llama.cpp source: `https://github.com/ggml-org/llama.cpp.git`
- llama.cpp commit: `05f2dcfdba3879c55f735efa0f124b1a56f7ed11`
- llama.cpp reported version: `0.4.1-dev` (build 1)
- Build: Release, `arm64-v8a`, `android-26`, CPU only, static C++/llama/ggml libraries, curl/OpenMP/Vulkan/OpenCL/tests disabled
- Runtime dependencies: Android system `libm.so`, `libdl.so`, and `libc.so` only; no companion `.so` files or `LD_LIBRARY_PATH` were required

Artifacts retained for the follow-up qualitative run:

- `/private/tmp/llama.cpp-build/build-android-arm64/bin/llama-bench` (106,591,264 bytes)
- `/private/tmp/llama.cpp-build/build-android-arm64/bin/llama-cli` (154,369,040 bytes)
- `/private/tmp/llama.cpp-build/models/Qwen_Qwen3-0.6B-Q4_K_M.gguf` (484,220,320 bytes)

The same three artifacts are present at `/data/local/tmp/llmspike/` on the device, with both binaries executable.

## Model

- File: `Qwen_Qwen3-0.6B-Q4_K_M.gguf`
- Quantization: Q4_K_M (llama.cpp identifies it as `qwen3 0.6B Q4_K - Medium`)
- File size: 484,220,320 bytes (461.79 MiB)
- Model tensor size reported by llama.cpp: 478,268,416 bytes
- Parameters reported by llama.cpp: 751,632,384
- Source: `https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF/resolve/main/Qwen_Qwen3-0.6B-Q4_K_M.gguf`
- Repository revision at lookup: `60b85c0e3d8fe0f6474f406922a26d12aca4550d`
- SHA-256: `9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14` (matched Hugging Face LFS metadata)

The official `Qwen/Qwen3-0.6B-GGUF` repository exposed Q8_0 but not Q4_K_M. The bartowski conversion had the exact requested quant and the expected roughly 460-500 MiB size, so no quantization substitution was necessary.

## Benchmark results

Each matrix entry is a separate invocation of the following shape, using llama-bench's default five measured repetitions and warmup:

```sh
/data/local/tmp/llmspike/llama-bench \
  -m /data/local/tmp/llmspike/Qwen_Qwen3-0.6B-Q4_K_M.gguf \
  -p 512 -n 128 -c <context> -t <threads> -o json
```

Values are arithmetic mean ± sample standard deviation as reported by llama-bench. Load time measures the `llama_model_load_from_file` call once per invocation; the mmap-based loader was used (`load_mode=auto`). The run order was 2048/4, 2048/2, 2048/8, 4096/2, 4096/4, 4096/8, followed by the memory rerun.

| Context | Threads | Load time (ms) | Prefill 512 (tok/s) | Decode 128 (tok/s) | Peak RSS / VmHWM |
|---:|---:|---:|---:|---:|---:|
| 2048 | 2 | 236.75 | 55.21 ± 0.20 | 30.17 ± 0.53 | — |
| 2048 | 4 | 232.73 | 89.59 ± 2.62 | 34.17 ± 1.71 | 775,312 kB (757.14 MiB)¹ |
| 2048 | 8 | 247.31 | 104.58 ± 0.99 | 36.03 ± 1.02 | — |
| 4096 | 2 | 591.83 | 47.13 ± 1.70 | 25.40 ± 3.06 | — |
| 4096 | 4 | 681.88 | 63.23 ± 1.10 | 32.42 ± 0.66 | — |
| 4096 | 8 | 635.40 | 78.97 ± 6.57 | 30.18 ± 1.11 | — |

¹ Memory came from a separate representative `-c 2048 -t 4 -r 1` rerun. `/proc/32221/status` was polled about once per second. Both the maximum observed `VmRSS` and the kernel-maintained `VmHWM` reached 775,312 kB. `dumpsys meminfo` was not used because this is a raw native executable rather than an app process.

The later 4096 runs show lower throughput and greater variance, especially at 8 threads. Battery temperature rose substantially over the uninterrupted sequence, so these rows include benchmark-order/thermal effects and should not be interpreted as a context-size-only comparison.

## Battery temperature proxy

- Before matrix: `temperature: 290` = **29.0°C**
- After matrix and memory rerun: `temperature: 379` = **37.9°C**
- Change: **+8.9°C**

These are `dumpsys battery` readings in tenths of a degree Celsius, used only as the requested cheap thermal proxy.

## Deviations and issues encountered

1. **Host architecture:** the host reports `x86_64`, not Apple Silicon. The official current LTS macOS archive was therefore appropriate; its toolchain prebuilt directory is `darwin-x86_64`.
2. **NDK install location:** the managed sandbox denied writes to `/Users/tng/Library/Android/sdk/ndk/30.0.16248370` with `Operation not permitted`. The verified archive was extracted to `/private/tmp/ndk-download/extracted/android-ndk-r30` and used directly. The toolchain check succeeded for `aarch64-linux-android26-clang`.
3. **Current llama.cpp CLI target:** current upstream only adds `llama-cli` when `LLAMA_BUILD_SERVER=ON`. That option was enabled and only the `llama-cli` target was built; no server executable was deployed. Current upstream also pulled/compiled server/MTMD support required by this CLI target.
4. **Current llama-bench lacks `-c`:** unmodified commit `05f2dcf` rejects `-c`, and otherwise allocates only `n_prompt + n_gen + n_depth`. Using `-d` would prefill extra tokens and contaminate the timed tests. A minimal throwaway local patch in `/private/tmp/llama.cpp-build/tools/llama-bench/llama-bench.cpp` restores `-c/--ctx-size` as allocation-only, emits `n_ctx`, and emits measured `load_time_ms`. Before the patch, `-c 2048` failed as an invalid parameter; after rebuilding, a device test reported `n_ctx: 2048` and completed successfully.
5. **Thermal/order sensitivity:** the full matrix was run consecutively, per the spike scope, without an elaborate cooldown or throttling protocol. The table reports the observed data rather than normalizing or hiding this effect.

No matrix combination failed or crashed.
