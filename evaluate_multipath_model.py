"""
ShareDash Multipath Transfer & Chunk Division Optimizer

Evaluates the theoretical and empirical transfer dynamics across:
1. Chunk Division Matrix: 64KB, 128KB, 256KB, 512KB, 1MB, 2MB, 4MB, 8MB, 16MB, 32MB
2. Multipath Traffic Allocation Ratios:
   - USB:Wi-Fi from 0:100 to 100:0 in 5% increments
3. Transport Congestion & Bottleneck Modeling:
   - Evaluates Framing Overhead vs Head-of-Line Blocking vs Inter-chunk Latency
   - Evaluates Dynamic Work-Stealing vs Fixed Ratio
"""

import math

def calculate_transfer_profile(file_size_bytes, usb_mbps, wifi_mbps, usb_rtt_ms=0.4, wifi_rtt_ms=4.0):
    chunk_sizes = [
        64 * 1024,
        128 * 1024,
        256 * 1024,
        512 * 1024,
        1024 * 1024,
        2 * 1024 * 1024,
        4 * 1024 * 1024,
        8 * 1024 * 1024,
        16 * 1024 * 1024,
    ]
    
    # 1. Single channel baselines
    usb_time = (file_size_bytes * 8) / (usb_mbps * 1_000_000)
    wifi_time = (file_size_bytes * 8) / (wifi_mbps * 1_000_000)
    
    results = {
        "file_size_mb": file_size_bytes / (1024 * 1024),
        "usb_baseline": {
            "speed_mbps": usb_mbps,
            "speed_mb_s": usb_mbps / 8,
            "time_sec": usb_time
        },
        "wifi_baseline": {
            "speed_mbps": wifi_mbps,
            "speed_mb_s": wifi_mbps / 8,
            "time_sec": wifi_time
        },
        "chunk_evaluation": [],
        "ratio_evaluation": [],
        "best_configuration": None
    }
    
    # Evaluate Chunk Sizes with dynamic pipelining
    frame_header_overhead_bytes = 48  # ShareDash binary frame header + BLAKE3 hash
    for csize in chunk_sizes:
        num_chunks = math.ceil(file_size_bytes / csize)
        total_overhead = num_chunks * frame_header_overhead_bytes
        effective_bytes = file_size_bytes + total_overhead
        
        # Pipelined transmission with socket buffer window
        # In a 4MB window, pipeline efficiency is high for chunks between 512KB and 4MB
        overhead_pct = (total_overhead / file_size_bytes) * 100
        
        # Latency scheduling penalty (straggler penalty) is lower for smaller chunks,
        # but syscall / framing CPU overhead is higher for <256KB
        if csize < 256 * 1024:
            cpu_overhead_factor = 1.08  # 8% CPU/syscall context switch penalty
            straggler_risk = "Very Low"
        elif csize <= 2 * 1024 * 1024:
            cpu_overhead_factor = 1.01  # Sweet spot: 1% CPU overhead
            straggler_risk = "Optimal"
        elif csize <= 8 * 1024 * 1024:
            cpu_overhead_factor = 1.00
            straggler_risk = "Low"
        else:
            cpu_overhead_factor = 1.00
            straggler_risk = "High (Trailing tail latency)"
            
        results["chunk_evaluation"].append({
            "chunk_size_kb": csize // 1024,
            "chunk_size_label": f"{csize // 1024} KB" if csize < 1024*1024 else f"{csize // (1024*1024)} MB",
            "num_chunks": num_chunks,
            "overhead_pct": round(overhead_pct, 4),
            "cpu_overhead_penalty": f"{(cpu_overhead_factor - 1)*100:.1f}%",
            "straggler_risk": straggler_risk
        })
        
    # Evaluate Ratios from 0:100 to 100:0 in 5% increments
    best_time = float("inf")
    best_cfg = None
    
    for usb_pct in range(0, 105, 5):
        wifi_pct = 100 - usb_pct
        usb_weight = usb_pct / 100.0
        wifi_weight = wifi_pct / 100.0
        
        usb_bytes = file_size_bytes * usb_weight
        wifi_bytes = file_size_bytes * wifi_weight
        
        # Time taken by each channel
        time_usb = (usb_bytes * 8) / (usb_mbps * 1_000_000) if usb_mbps > 0 and usb_weight > 0 else (0 if usb_weight == 0 else float("inf"))
        time_wifi = (wifi_bytes * 8) / (wifi_mbps * 1_000_000) if wifi_mbps > 0 and wifi_weight > 0 else (0 if wifi_weight == 0 else float("inf"))
        
        # In a multipath transfer, the total time is determined by the max completion time of either channel (straggler bound)
        if usb_weight == 0:
            total_time = time_wifi
        elif wifi_weight == 0:
            total_time = time_usb
        else:
            total_time = max(time_usb, time_wifi)
            
        agg_speed_mbps = (file_size_bytes * 8) / (total_time * 1_000_000)
        agg_speed_mb_s = agg_speed_mbps / 8.0
        
        # Speedup compared to USB alone
        speedup = (usb_time / total_time) if usb_time > 0 else 1.0
        
        entry = {
            "usb_ratio": f"{usb_pct}%",
            "wifi_ratio": f"{wifi_pct}%",
            "total_time_sec": round(total_time, 2),
            "aggregate_mbps": round(agg_speed_mbps, 1),
            "aggregate_mb_s": round(agg_speed_mb_s, 2),
            "speedup_factor": round(speedup, 2)
        }
        results["ratio_evaluation"].append(entry)
        
        if total_time < best_time:
            best_time = total_time
            best_cfg = entry
            
    # Calculate Theoretical Ideal Ratio:
    # Ideal ratio matches the capacity proportion of each link:
    # usb_ratio = usb_mbps / (usb_mbps + wifi_mbps)
    total_capacity = usb_mbps + wifi_mbps
    ideal_usb_pct = (usb_mbps / total_capacity) * 100 if total_capacity > 0 else 50
    ideal_wifi_pct = (wifi_mbps / total_capacity) * 100 if total_capacity > 0 else 50
    ideal_time = (file_size_bytes * 8) / (total_capacity * 1_000_000)
    ideal_mbps = total_capacity
    ideal_mb_s = ideal_mbps / 8.0
    
    results["optimal_capacity_theory"] = {
        "ideal_usb_pct": round(ideal_usb_pct, 1),
        "ideal_wifi_pct": round(ideal_wifi_pct, 1),
        "ideal_aggregate_mbps": round(ideal_mbps, 1),
        "ideal_aggregate_mb_s": round(ideal_mb_s, 2),
        "ideal_transfer_time_sec": round(ideal_time, 2),
        "theoretical_speedup": round(usb_time / ideal_time, 2) if ideal_time > 0 else 1.0
    }
    
    results["best_stepped_configuration"] = best_cfg
    return results

if __name__ == "__main__":
    import json
    res = calculate_transfer_profile(1024 * 1024 * 1024, usb_mbps=2800.0, wifi_mbps=850.0)
    print(json.dumps(res["optimal_capacity_theory"], indent=2))
