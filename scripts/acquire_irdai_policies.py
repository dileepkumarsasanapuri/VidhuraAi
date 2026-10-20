#!/usr/bin/env python3
"""
Official IRDAI Health Insurance Policy Document Acquisition Script.

Acquires official, publicly registered policy wording documents from the statutory
IRDAI repository (https://irdai.gov.in/health-insurance-products) and validates their
cryptographic SHA-256 integrity upon download.

Security & Integrity Guarantees:
- Official Source Allowlisting: Downloads strictly restricted to https://irdai.gov.in/documents/
- Cryptographic Integrity: Validates SHA-256 against known immutable benchmarks.
- Zero Credentials: Uses only standard public HTTP headers; no cookies, tokens, or credentials.
- Non-destructive: Writes only to the designated target evidence directory within repository.
"""

import os
import sys
import hashlib
from urllib.parse import urlparse
import requests

ALLOWED_DOMAIN = "irdai.gov.in"
ALLOWED_PATH_PREFIX = "/documents/37343/931203/"

# Expected authentic public policy catalogue records
OFFICIAL_POLICIES = [
    {
        "uin": "UIIHLIP22070V042122",
        "policy_name": "Family Medicare Policy",
        "insurer": "United India Insurance Co. Ltd.",
        "filename": "UIIHLIP22070V042122_HEALTH2068.pdf",
        "expected_bytes": 1583059,
        "expected_sha256": "6bb20e21fa1c97b514f57ae6764b2e9b7a22df71d8afb2ece0539e0e17eea267",
        "url": "https://irdai.gov.in/documents/37343/931203/UIIHLIP22070V042122_HEALTH2068.pdf/ade80179-0e46-3c62-a889-652663255c71?version=1.1&t=1668769400360"
    },
    {
        "uin": "NIAHLIP21279V032021",
        "policy_name": "New India Premier Mediclaim Policy",
        "insurer": "The New India Assurance Co. Ltd.",
        "filename": "NIAHLIP21279V032021_2020-2021.pdf",
        "expected_bytes": 944229,
        "expected_sha256": "17c54a21891f9be3f5e0cfd8579eb120f2eceb14aaba2fa695b62c1ab45f3191",
        "url": "https://irdai.gov.in/documents/37343/931203/NIAHLIP21279V032021_2020-2021.pdf/add6f0c6-5e09-f925-10ca-8ace0c2d8c2e?version=1.1&t=1668594975860"
    },
    {
        "uin": "NIAHLIP21277V042021",
        "policy_name": "New India Mediclaim Policy (Revision 4 - Archived)",
        "insurer": "The New India Assurance Co. Ltd.",
        "filename": "NIAHLIP21277V042021_2020-2021.pdf",
        "expected_bytes": 929569,
        "expected_sha256": "bfc304901063fa21b1e145c22d2ee15bd5f8190e82b4caca069cfe96dbcedc9f",
        "url": "https://irdai.gov.in/documents/37343/931203/NIAHLIP21277V042021_2020-2021.pdf/31273485-8a70-a74c-04be-e4847c286349?version=1.1&t=1668594892782"
    }
]

def acquire_policies():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    dest_dir = os.path.join(repo_root, "backend", "src", "main", "resources", "evidence", "policies")
    os.makedirs(dest_dir, exist_ok=True)

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "https://irdai.gov.in/health-insurance-products"
    }

    print(f"[IRDAI Ingestion] Target Directory: {dest_dir}")
    all_success = True

    for p in OFFICIAL_POLICIES:
        uin = p["uin"]
        fn = p["filename"]
        url = p["url"]

        # 1. URL Domain and Path Allowlist Check
        parsed = urlparse(url)
        if parsed.netloc != ALLOWED_DOMAIN:
            print(f"[ERROR] Security Violation: URL domain '{parsed.netloc}' is not allowed!")
            all_success = False
            continue
        if not parsed.path.startswith(ALLOWED_PATH_PREFIX):
            print(f"[ERROR] Security Violation: Path '{parsed.path}' is outside allowed prefix!")
            all_success = False
            continue

        file_path = os.path.join(dest_dir, fn)
        
        # Check if already acquired and valid
        if os.path.exists(file_path):
            existing_bytes = open(file_path, "rb").read()
            existing_sha = hashlib.sha256(existing_bytes).hexdigest()
            if existing_sha == p["expected_sha256"] and len(existing_bytes) == p["expected_bytes"]:
                print(f"[VERIFIED] {fn} already exists with authentic SHA-256: {existing_sha}")
                continue

        print(f"[DOWNLOADING] {uin} ({fn}) from official IRDAI repository...")
        try:
            resp = requests.get(url, headers=headers, timeout=30)
            if resp.status_code == 200:
                data = resp.content
                sha = hashlib.sha256(data).hexdigest()

                if len(data) != p["expected_bytes"]:
                    print(f"[WARNING] Size variance for {fn}: received {len(data)} vs expected {p['expected_bytes']}")

                if sha != p["expected_sha256"]:
                    print(f"[ERROR] SHA-256 mismatch for {fn}!\n  Got:      {sha}\n  Expected: {p['expected_sha256']}")
                    all_success = False
                    continue

                with open(file_path, "wb") as f:
                    f.write(data)
                print(f"[SUCCESS] {fn} saved. Bytes: {len(data)}, SHA-256: {sha}")
            else:
                print(f"[FAILURE] HTTP {resp.status_code} when downloading {fn}")
                all_success = False
        except Exception as e:
            print(f"[ERROR] Exception during download of {fn}: {e}")
            all_success = False

    return all_success

if __name__ == "__main__":
    success = acquire_policies()
    sys.exit(0 if success else 1)
