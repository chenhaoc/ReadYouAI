# Build Script Profiles Design

Date: 2026-04-11

## Goal

Reshape the local Android build script so it clearly supports:

- fast debug builds for rapid device testing
- real release builds
- a resource profile switch for release builds between full-machine usage and single-core usage

The default behavior should favor speed on an Apple Silicon Mac M1.

## Command Interface

The build entry point remains:

```bash
./scripts/build-github-debug.sh
```

The script will support a build mode plus an optional resource profile:

```bash
./scripts/build-github-debug.sh [fast-debug|release|<gradle-task>] [--profile full|1core]
```

Supported defaults:

- no arguments: `fast-debug --profile full`
- `fast-debug`: `assembleGithubAiDebug --profile full`
- `release`: `assembleGithubAiRelease --profile full`

Supported explicit examples:

```bash
./scripts/build-github-debug.sh fast-debug
./scripts/build-github-debug.sh fast-debug --profile 1core
./scripts/build-github-debug.sh release
./scripts/build-github-debug.sh release --profile 1core
./scripts/build-github-debug.sh assembleGithubAiRelease --profile full
./scripts/build-github-debug.sh installGithubAiDebug --profile full
```

## Build Modes

### fast-debug

Purpose: produce the quickest local APK for testing current changes.

Defaults:

- Gradle task: `assembleGithubAiDebug`
- daemon enabled
- parallel enabled
- build cache enabled
- resource profile defaults to `full`

### release

Purpose: produce the real signed release APK.

Defaults:

- Gradle task: `assembleGithubAiRelease`
- resource profile defaults to `full`
- single-core mode is opt-in through `--profile 1core`

## Resource Profiles

### full

Purpose: use all available local machine capacity.

Behavior:

- detect logical CPU count from macOS
- set `--max-workers` to detected CPU count
- set `org.gradle.workers.max` to detected CPU count
- set `java.util.concurrent.ForkJoinPool.common.parallelism` to detected CPU count
- do not force `ActiveProcessorCount=1`

For M1 hardware, this should normally resolve to 8 logical cores.

### 1core

Purpose: minimize load for background or conservative release builds.

Behavior:

- set workers and common parallelism to `1`
- set `ActiveProcessorCount=1`
- keep current conservative JVM settings for release-style low-load execution

## Task Inference Rules

If the first positional argument is:

- `fast-debug`: use fast debug mode
- `release`: use release mode
- any Gradle task name: infer mode from task name

Task inference:

- tasks containing `Release` default to release mode
- all other tasks default to fast-debug mode

The explicit `--profile` flag overrides the default profile for the chosen mode.

## JVM And Gradle Strategy

### fast-debug + full

Use aggressive local iteration settings:

- daemon
- parallel
- build cache
- Kotlin compiler daemon mode
- worker count based on detected logical CPU count

### release + full

Use the real release task, but still allow full local machine utilization:

- daemon enabled
- parallel enabled
- build cache enabled
- still set worker count based on detected logical CPU count
- do not force single-core JVM limits

### release + 1core

Retain the existing low-load release path:

- `--no-daemon`
- `--max-workers=1`
- `ActiveProcessorCount=1`
- Kotlin compiler in-process

## Documentation

Update the build guide so it explains:

- the new command syntax
- default behavior for each mode
- the two resource profiles
- concrete examples for fast local testing and real release packaging

## Validation

Use lightweight script-level tests that do not trigger real Gradle builds.

Required checks:

- default invocation resolves to `fast-debug --profile full`
- `release` resolves to `assembleGithubAiRelease --profile full`
- `release --profile 1core` applies the single-core flags
- explicit Gradle tasks can still be passed through with a profile
- script syntax remains valid
