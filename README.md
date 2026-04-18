# toyaria

> A parallel, resumable HTTP file downloader implemented in Kotlin.

`toyaria` is a command-line downloader for large HTTP files.

It splits a file into byte ranges, downloads ranges concurrently, retries transient failures with exponential backoff, and can resume interrupted sessions from persisted state.

The implementation focuses on predictable behavior for long-running downloads.

## Table of Contents

- [Problem Context](#problem-context)
- [Main Capabilities](#main-capabilities)
- [How the System Works](#how-the-system-works)
- [Architecture](#architecture)
- [Reliability Model](#reliability-model)
- [Requirements](#requirements)
- [Build and Run](#build-and-run)
- [CLI Options](#cli-options)
- [Usage Examples](#usage-examples)
- [Extended Examples](#extended-examples)
- [Resume Semantics](#resume-semantics)
- [Integrity Verification](#integrity-verification)
- [Configuration Defaults](#configuration-defaults)
- [Tests](#tests)
- [Project Layout](#project-layout)
- [Dependencies](#dependencies)

## Problem Context

Large HTTP downloads can fail due to transient network issues, temporary server-side failures, or long single-stream transfers.

`toyaria` addresses this by:

- parallelizing transfer over independent byte ranges;
- retrying failed range requests instead of restarting everything;
- persisting progress so interrupted runs can continue;
- optionally validating end-to-end integrity with SHA-256.

This approach reduces the need to restart full transfers after partial failures.

## Main Capabilities

- **Parallel range downloading**: the file is partitioned into configurable chunks and downloaded concurrently.
- **Worker balancing through a shared queue**: free workers pick the next pending chunk from a shared queue.
- **Direct assembly on disk**: each chunk is written at its final byte offset in a pre-sized destination file (no merge phase).
- **Per-chunk retry with exponential backoff**: transient failures are retried before the transfer is considered failed.
- **Resume support**: state is persisted to a JSON sidecar file and reused on subsequent runs.
- **Optional full-file checksum validation**: a provided SHA-256 digest is verified at the end.
- **Terminal progress output**: percentage, throughput, and ETA are rendered to `stderr`.

## How the System Works

At startup, the downloader checks remote metadata via `HEAD` and verifies that the server supports byte ranges (`Accept-Ranges: bytes`) and exposes file size (`Content-Length`).

It then:

1. computes chunk boundaries over the full byte interval;
2. restores previous state when resume is enabled;
3. enqueues pending chunks to a shared work queue;
4. starts multiple coroutine workers that consume chunks concurrently;
5. writes bytes directly into the destination at exact offsets;
6. emits progress events to the terminal layer;
7. updates persisted state after chunk completion;
8. optionally validates full-file SHA-256 and clears state on success.

## Architecture

The project follows a strict MVC separation:

- **Model**: immutable domain state (download configuration, chunk metadata, persisted state snapshots, event model).
- **Controller**: download engine (remote metadata retrieval, scheduling, worker execution, retry policy, file writing, resume persistence, checksum validation).
- **View**: command-line interface and progress rendering.

An event stream is the boundary between Controller and View. The engine emits lifecycle events, while the terminal layer only observes and renders them. This keeps orchestration and presentation decoupled.

## Reliability Model

`toyaria` combines several reliability mechanisms:

- **Retry strategy**: each chunk download uses bounded retries with exponential backoff.
- **Crash/interruption tolerance**: persisted state tracks completed vs pending ranges.
- **Deterministic resume**: only pending chunks are rescheduled on restart.
- **Integrity checks**:
  - per-chunk digests are recorded in resume state;
  - optional full-file digest verifies final correctness.

If checksum verification is requested and fails, the command exits with a non-zero status.

## Requirements

- **JDK**: 25 (the project build uses a JVM toolchain set to Java 25)
- **OS**: any platform supported by Java and Gradle
- **Gradle**: wrapper included (`./gradlew`), no global installation required

## Build and Run

Build and run with the Gradle wrapper:

```bash
./gradlew build
./gradlew run --args="URL"
```

Run with explicit options:

```bash
./gradlew run --args="URL -o ./output.bin -n 8 -s 16MB"
```

Build an executable artifact and run it directly:

```bash
./gradlew jar
java -jar build/libs/toyaria-1.0-SNAPSHOT.jar URL
```

## CLI Options

```text
Usage: toyaria [OPTIONS] URL

Arguments:
  URL                       URL of the file to download.

Options:
  -o, --output PATH         Destination file path.
  -n, --workers INT         Number of parallel workers (default: 4).
  -s, --chunk-size TEXT     Chunk size, e.g. 4MB, 16MB (default: 8MB).
  --no-resume               Disable resume.
  --checksum SHA256         Expected SHA-256 digest for full-file verification.
  --version                 Print version and exit.
  -h, --help                Show help and exit.
```

Notes:

- chunk size parsing is case-insensitive (`kb`, `mb`, `gb`, or bytes);
- when output is omitted, filename is inferred from URL path;
- if inference is not possible, a safe fallback filename is used.

## Usage Examples

Basic download:

```bash
./gradlew run --args="http://localhost:8080/demo.mp4"
```

Custom output path:

```bash
./gradlew run --args="http://localhost:8080/demo.mp4 -o ./demo.mp4"
```

More workers and larger chunks:

```bash
./gradlew run --args="http://localhost:8080/demo.mp4 -n 8 -s 16MB"
```

Checksum verification:

```bash
./gradlew run --args="http://localhost:8080/demo.mp4 --checksum <sha256-hex>"
```

Disable resume:

```bash
./gradlew run --args="http://localhost:8080/demo.mp4 --no-resume"
```

## Extended Examples

For a complete set of reproducible examples (including fixture generation, local server setup, resume demonstration, and checksum success/failure scenarios), see:

- `examples/README.md`

## Resume Semantics

Resume state is stored as a JSON sidecar next to the destination file:

```text
.<destination-filename>.toyaria-state.json
```

The state captures:

- source URL;
- remote file size;
- configured chunk size;
- per-chunk status and checksum.

On restart with resume enabled, only chunks still marked as pending are enqueued.

## Integrity Verification

Two integrity layers are available:

- **Chunk-level digest tracking** during download for state consistency.
- **Optional full-file SHA-256 validation** at completion (`--checksum`).

When full-file validation is requested, mismatches terminate the command with a non-zero exit status.

## Configuration Defaults

| Parameter | Default |
| --- | --- |
| Workers | `4` |
| Chunk size | `8MB` |
| Retry attempts (per chunk) | `3` |
| Retry initial delay | `500ms` |
| Retry backoff factor | `2.0` |
| Resume | enabled |

## Tests

Run the test suite with:

```bash
./gradlew test
```

The suite includes unit and integration coverage for HTTP behavior, chunk partitioning, file assembly, state persistence, checksum utilities, and end-to-end download scenarios with a mock HTTP server.

## Project Layout

```text
src/
  main/kotlin/
    Main.kt
    it/toyaria/
      model/
      controller/
      view/
  test/kotlin/it/toyaria/
```

## Dependencies

### Runtime

| Library | Version | Purpose |
| --- | --- | --- |
| Kotlin | `2.3.20` | Language and standard tooling |
| Ktor CIO Client | `3.4.2` | Asynchronous HTTP client |
| Kotlin Coroutines | `1.10.2` | Structured concurrency and channels |
| Clikt | `5.1.0` | Command-line parsing |
| kotlinx-serialization-json | `1.10.0` | Resume state serialization |
| Logback Classic | `1.5.32` | Logging backend |

### Test

| Library | Version | Purpose |
| --- | --- | --- |
| kotlin-test-junit5 | `2.3.20` | Test framework integration |
| kotlinx-coroutines-test | `1.10.2` | Coroutine test utilities |
| MockK | `1.14.9` | Mocking |
| mockwebserver3 | `5.3.0` | In-process HTTP test server |

### Quality and Coverage Tooling

| Tool | Version |
| --- | --- |
| detekt | `2.0.0-alpha.2` |
| ktfmt | `0.26.0` |
| kover | `0.9.8` |

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0).

See `LICENSE` for the full license text.

