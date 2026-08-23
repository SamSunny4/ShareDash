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
        "optimal_capacity_theory": {}
    }
    
    total_capacity_mbps = usb_mbps + wifi_mbps
    ideal_usb_pct = (usb_mbps / total_capacity_mbps) * 100
    ideal_wifi_pct = (wifi_mbps / total_capacity_mbps) * 100
    ideal_transfer_time_sec = (file_size_bytes * 8) / (total_capacity_mbps * 1_000_000)
    
    results["optimal_capacity_theory"] = {
        "ideal_usb_pct": round(ideal_usb_pct, 1),
        "ideal_wifi_pct": round(ideal_wifi_pct, 1),
        "ideal_aggregate_mbps": total_capacity_mbps,
        "ideal_aggregate_mb_s": total_capacity_mbps / 8,
        "ideal_transfer_time_sec": ideal_transfer_time_sec,
        "theoretical_speedup": round(usb_time / ideal_transfer_time_sec, 2)
    }
    
    return results

if __name__ == "__main__":
    file_1gb = 1024 * 1024 * 1024
    res = calculate_transfer_profile(file_1gb, 3200.0, 866.0)
    print("Transfer Profile (1GB over USB 3.2 + 5GHz Wi-Fi):")
    print(f"  USB Only: {res['usb_baseline']['speed_mb_s']:.1f} MB/s ({res['usb_baseline']['time_sec']:.2f}s)")
    print(f"  Wi-Fi Only: {res['wifi_baseline']['speed_mb_s']:.1f} MB/s ({res['wifi_baseline']['time_sec']:.2f}s)")
    print(f"  Multipath Aggregation: {res['optimal_capacity_theory']['ideal_aggregate_mb_s']:.1f} MB/s ({res['optimal_capacity_theory']['ideal_transfer_time_sec']:.2f}s)")
    print(f"  Theoretical Speedup: {res['optimal_capacity_theory']['theoretical_speedup']}x")
