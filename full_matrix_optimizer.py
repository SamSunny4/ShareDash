import json
from evaluate_multipath_model import calculate_transfer_profile

def generate_full_evaluation_report():
    # Model parameters for standard high-performance phone (USB 3.x / USB 2.0 and Wi-Fi 6E/5GHz)
    # Scenario 1: USB 3.x Cable (3.2 Gbps) + 5GHz Wi-Fi (866 Mbps)
    # Scenario 2: USB 2.0 Cable (480 Mbps) + 5GHz Wi-Fi (866 Mbps)
    
    scenarios = [
        {"name": "USB 3.2 Cable (3.2 Gbps) + 5GHz Wi-Fi 6 (1200 Mbps)", "usb_mbps": 3200.0, "wifi_mbps": 1200.0},
        {"name": "USB 3.0 Cable (2.5 Gbps Real-world) + 5GHz Hotspot (866 Mbps)", "usb_mbps": 2500.0, "wifi_mbps": 866.0},
        {"name": "USB 2.0 Cable (480 Mbps) + 5GHz Hotspot (866 Mbps)", "usb_mbps": 480.0, "wifi_mbps": 866.0},
    ]
    
    all_reports = []
    file_size_1gb = 1024 * 1024 * 1024
    
    for sc in scenarios:
        res = calculate_transfer_profile(file_size_1gb, sc["usb_mbps"], sc["wifi_mbps"])
        res["scenario_name"] = sc["name"]
        all_reports.append(res)
        
    return all_reports

if __name__ == "__main__":
    reports = generate_full_evaluation_report()
    for r in reports:
        print(f"\n==================== {r['scenario_name']} ====================")
        print(f"USB Baseline   : {r['usb_baseline']['speed_mb_s']:.1f} MB/s ({r['usb_baseline']['time_sec']:.2f}s)")
        print(f"Wi-Fi Baseline : {r['wifi_baseline']['speed_mb_s']:.1f} MB/s ({r['wifi_baseline']['time_sec']:.2f}s)")
        print(f"Ideal Ratio    : {r['optimal_capacity_theory']['ideal_usb_pct']}% USB : {r['optimal_capacity_theory']['ideal_wifi_pct']}% Wi-Fi")
        print(f"Combined Speed : {r['optimal_capacity_theory']['ideal_aggregate_mb_s']:.1f} MB/s (~{r['optimal_capacity_theory']['ideal_aggregate_mbps']/1000:.2f} Gbps) in {r['optimal_capacity_theory']['ideal_transfer_time_sec']:.2f}s (Speedup: {r['optimal_capacity_theory']['theoretical_speedup']}x)")
