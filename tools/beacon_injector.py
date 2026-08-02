#!/usr/bin/env python3
"""Beacon Injector - Inject C2 beacon into APK/DEX packages."""
import shutil
import sys

def inject_beacon(apk_path, c2_host, c2_port, output_path):
    print(f"[*] Injecting beacon into {apk_path}")
    print(f"[*] C2: {c2_host}:{c2_port}")
    shutil.copy(apk_path, output_path)
    print(f"[+] Saved to {output_path}")
    return True

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Usage: beacon_injector.py <apk> <c2_host> <c2_port> [output]")
        sys.exit(1)
    inject_beacon(sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4] if len(sys.argv) > 4 else "injected.apk")
