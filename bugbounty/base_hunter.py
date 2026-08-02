# Base Hunter - shared utilities for bug bounty scanners

import json
import os

def load_config(path):
    if not os.path.exists(path):
        raise FileNotFoundError(f"Config not found: {path}")
    with open(path) as f:
        return json.load(f)

class BaseHunter:
    def __init__(self):
        self.results = []

    def save_results(self, output_path):
        with open(output_path, "w") as f:
            json.dump(self.results, f, indent=2)
        print(f"[+] Results saved to {output_path}")
