# Diagnostic and benchmark scripts

These Python utilities support development experiments. They are not an automated release certification suite. Review each script's paths and device assumptions before execution.

| Script | Purpose | Effects |
| --- | --- | --- |
| `generate_test_file.py` | Create a deterministic 1 GiB fixture and SHA-256 sidecar | Writes `sharedash_1gb_benchmark.bin` and its `.sha256` file in the repository root |
| `benchmark_suite.py` | Exercise the Android HTTP upload path over ADB | Can install/start the Android app, configure forwarding, generate a fixture, and send data |
| `run_benchmarks_live.py` | Live device probing and benchmark setup | Uses ADB and a connected phone; review installation and forwarding actions |
| `evaluate_multipath.py` | Evaluate transfer assumptions and fixture setup | Can generate the 1 GiB fixture |
| `evaluate_multipath_model.py` | Analytical multipath model | Produces model estimates rather than measured device throughput |
| `full_matrix_optimizer.py` | Compare modeled transfer configurations | Produces simulation results rather than physical benchmarks |

## Prepare a fixture

From the repository root:

```powershell
python scripts/generate_test_file.py
```

Allow at least 1 GiB of free space. The generator uses a repeated deterministic byte pattern, not random data.

## Run a model

```powershell
python scripts/evaluate_multipath_model.py
python scripts/full_matrix_optimizer.py
```

Label the results as modeled or simulated. They do not demonstrate a device's achievable speed.

## Run a hardware experiment

Open the Android companion on a test phone and verify an authorized ADB connection. The live scripts assume Windows and use paths such as `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. Check their constants against your installation.

```powershell
python scripts/benchmark_suite.py
```

The benchmark suite uses the multipart `/api/v1/transfers/send` endpoint. A result from that path is not automatically a benchmark of the desktop GUI's `/api/v1/transfers/chunk` dispatcher.

## Record results consistently

For each run, record the commit/build, source and destination devices, transfer direction, USB cable/port, Wi-Fi adapter/band, file size in bytes, connection mode, elapsed time, and source/destination SHA-256.

Compare single-channel and combined modes with the same file and hardware. Report decimal MB/s or binary MiB/s explicitly. Do not equate the adapter's PHY link rate with file throughput.
