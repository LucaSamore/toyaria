#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXAMPLES_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FIXTURES_DIR="$EXAMPLES_DIR/generated/fixtures"
PORT="${1:-8080}"

if [[ ! -d "$FIXTURES_DIR" ]]; then
  echo "Fixtures not found. Run: $SCRIPT_DIR/prepare-fixtures.sh"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for this script."
  echo "Alternatively, use any HTTP server that supports byte ranges."
  exit 1
fi

echo "Serving fixtures from: $FIXTURES_DIR"
echo "URL base: http://localhost:$PORT"
echo "Press Ctrl+C to stop."

exec docker run --rm \
  -p "$PORT":80 \
  -v "$FIXTURES_DIR":/usr/local/apache2/htdocs/:ro \
  httpd:2.4

