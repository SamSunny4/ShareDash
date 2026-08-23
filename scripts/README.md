# 🛠️ ShareDash Diagnostic & Benchmarking Scripts

This directory contains benchmarking, simulation, and diagnostic scripts used to evaluate ShareDash multipath aggregation, calculate optimal chunk ratios, and run live ADB transfers.

## 📁 Script Overview

| Script | Purpose |
| :--- | :--- |
| `benchmark_suite.py` | Full end-to-end automated benchmark suite connecting to an Android phone over ADB / USB Fast-Path and measuring real-world transfer throughput. |
| `generate_test_file.py` | Generates deterministic 1 GiB pseudo-random binary test files with SHA-256 validation signatures for integrity testing. |
| `evaluate_multipath.py` | Streaming transfer tester with configurable socket buffers and chunk boundaries. |
| `evaluate_multipath_model.py` | Analytical model calculating optimal work-stealing ratios, framing overhead, and bandwidth aggregation across USB 3.x, USB 2.0, and 5 GHz Wi-Fi. |
| `full_matrix_optimizer.py` | Matrix simulation report running dynamic chunk evaluation across diverse hardware profiles. |
| `run_benchmarks_live.py` | Quick utility to verify ADB port forward (`tcp:54325 -> tcp:54321`) and probe phone connectivity. |

## 🚀 Running Benchmarks

### 1. Generate Test File
```bash
python scripts/generate_test_file.py
```

### 2. Run Analytical Transfer Optimization
```bash
python scripts/full_matrix_optimizer.py
```

### 3. Run Live ADB Phone Benchmark
```bash
python scripts/benchmark_suite.py
```
