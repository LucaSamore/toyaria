# Examples for toyaria

This folder contains reproducible, lightweight examples for all primary CLI use cases of `toyaria`.

The examples are intentionally designed to avoid committing large binary files. Test assets are generated locally and ignored by Git.

## What You Will Find

- deterministic fixture generation (64 MiB)
- local HTTP server setup with byte-range support
- example commands for each CLI option
- a resume workflow demonstration (interrupt and continue)
- checksum verification examples (success and failure)

## Why This Stays Lightweight

No large assets are stored in the repository.

- generated files are written under `examples/generated/`
- that directory is ignored by `examples/.gitignore`
- download outputs are written under `examples/downloads/` and ignored as well

You can regenerate everything at any time.

## Prerequisites

- JDK 25 (matching project toolchain)
- Docker (used by the server helper script)
- `bash`

## Folder Structure

```text
examples/
  README.md
  .gitignore
  scripts/
    cleanup-examples.sh
    prepare-fixtures.sh
    start-http-server.sh
```

## 1) Prepare Local Fixtures

Run from project root:

```bash
bash examples/scripts/prepare-fixtures.sh
```

This generates:

- `examples/generated/fixtures/demo-64mb.bin`
- `examples/generated/fixtures/demo-64mb.bin.sha256`

## 2) Start a Local HTTP Server

```bash
bash examples/scripts/start-http-server.sh
```

Optional custom port:

```bash
bash examples/scripts/start-http-server.sh 8081
```

The server serves files from `examples/generated/fixtures/`.

## 3) Verify Byte-Range Support

In another terminal:

```bash
curl -I http://localhost:8080/demo-64mb.bin
```

Expected: response headers should include `Accept-Ranges: bytes` and `Content-Length`.

## 4) Run Examples (All Main Use Cases)

All commands below are executed from project root.

### 4.1 Basic download

```bash
./gradlew run --args="http://localhost:8080/demo-64mb.bin"
```

### 4.2 Custom output path (`-o`)

```bash
mkdir -p examples/downloads
./gradlew run --args="http://localhost:8080/demo-64mb.bin -o examples/downloads/basic.bin"
```

### 4.3 Custom workers and chunk size (`-n`, `-s`)

```bash
./gradlew run --args="http://localhost:8080/demo-64mb.bin -o examples/downloads/parallel.bin -n 8 -s 1MB"
```

### 4.4 Checksum verification success (`--checksum`)

Read the expected checksum from the generated file:

```bash
EXPECTED_SHA=$(awk '{print $1}' examples/generated/fixtures/demo-64mb.bin.sha256)
./gradlew run --args="http://localhost:8080/demo-64mb.bin -o examples/downloads/check-ok.bin --checksum $EXPECTED_SHA"
```

### 4.5 Checksum verification failure

```bash
./gradlew run --args="http://localhost:8080/demo-64mb.bin -o examples/downloads/check-fail.bin --checksum deadbeef"
```

Expected: non-zero exit status after download completes.

### 4.6 Resume workflow (default behavior)

Start a download and interrupt it with `Ctrl+C` after a short delay:

```bash
./gradlew run --args="http://localhost:8080/demo-64mb.bin -o examples/downloads/resume.bin -s 512KB"
```

Run the same command again:

```bash
./gradlew run --args="http://localhost:8080/demo-64mb.bin -o examples/downloads/resume.bin -s 512KB"
```

Expected behavior:

- a sidecar state file is created near the destination during transfer
- completed chunks are reused on restart
- state file is removed after successful completion

### 4.7 Disable resume (`--no-resume`)

```bash
./gradlew run --args="http://localhost:8080/demo-64mb.bin -o examples/downloads/no-resume.bin --no-resume"
```

### 4.8 Help and version

```bash
./gradlew run --args="--help"
./gradlew run --args="--version"
```

## Optional: Run Using the Built JAR

```bash
./gradlew jar
java -jar build/libs/toyaria-1.0-SNAPSHOT.jar http://localhost:8080/demo-64mb.bin -o examples/downloads/jar-run.bin
```

## Troubleshooting

- If you do not see `Accept-Ranges: bytes`, use a different local server setup.
- If port `8080` is busy, start the server on another port and update URLs accordingly.
- If Docker is unavailable, you can use any HTTP server that supports range requests.

## Cleanup

Remove all example artifacts (generated fixtures, downloads, and temp files):

```bash
bash examples/scripts/cleanup-examples.sh
```

