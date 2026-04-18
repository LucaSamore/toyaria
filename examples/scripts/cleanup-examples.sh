#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXAMPLES_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

TARGETS=(
  "$EXAMPLES_DIR/generated"
  "$EXAMPLES_DIR/downloads"
  "$EXAMPLES_DIR/tmp"
)

for target in "${TARGETS[@]}"; do
  if [[ -e "$target" ]]; then
    rm -rf "$target"
    echo "Removed: $target"
  else
    echo "Skipped (not found): $target"
  fi
done

echo "Examples cleanup completed."

