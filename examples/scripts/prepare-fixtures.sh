#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXAMPLES_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FIXTURES_DIR="$EXAMPLES_DIR/generated/fixtures"

mkdir -p "$FIXTURES_DIR"

TARGET_FILE="$FIXTURES_DIR/demo-64mb.bin"
CHECKSUM_FILE="$FIXTURES_DIR/demo-64mb.bin.sha256"

python3 - "$TARGET_FILE" <<'PY'
from pathlib import Path
import hashlib
import sys

output = Path(sys.argv[1])
size_bytes = 64 * 1024 * 1024
chunk = bytes((i % 251 for i in range(4096)))

output.parent.mkdir(parents=True, exist_ok=True)
with output.open("wb") as f:
    remaining = size_bytes
    while remaining > 0:
        part = chunk if remaining >= len(chunk) else chunk[:remaining]
        f.write(part)
        remaining -= len(part)

digest = hashlib.sha256(output.read_bytes()).hexdigest()
print(digest)
PY

SHA256_VALUE="$(python3 - "$TARGET_FILE" <<'PY'
from pathlib import Path
import hashlib
import sys
path = Path(sys.argv[1])
print(hashlib.sha256(path.read_bytes()).hexdigest())
PY
)"

echo "$SHA256_VALUE  demo-64mb.bin" >"$CHECKSUM_FILE"

echo "Fixture created: $TARGET_FILE"
echo "Checksum saved: $CHECKSUM_FILE"
echo "SHA-256: $SHA256_VALUE"

