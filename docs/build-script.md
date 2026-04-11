# Build Script Guide

This repository includes a one-command build script at:

```bash
./scripts/build-github-debug.sh
```

Despite the file name, the script now defaults to a fast local `githubAiDebug` build and supports an explicit resource profile switch.

## What It Does

The script supports two local build modes plus a resource profile:

1. `fast-debug` for the fastest repeatable local testing APK
2. `release` for the real signed release APK
3. `--profile full|1core` to choose full-machine or single-core execution

It does the following:

1. Detects `JAVA_HOME`, defaulting to Homebrew OpenJDK 21 on Apple Silicon macOS.
2. Detects `ANDROID_SDK_ROOT`, defaulting to `~/Library/Android/sdk`.
3. Creates `local.properties` automatically if it does not exist.
4. Disables inherited proxy variables to avoid broken local proxy settings affecting Gradle dependency downloads.
5. Parses `./scripts/build-github-debug.sh [fast-debug|release|<gradle-task>] [--profile full|1core]`.
6. Defaults both `fast-debug` and `release` to `--profile full`.
7. Detects logical CPU count for `--profile full`.
8. Prints the final APK path after a successful build.

## Default Usage: Fast Debug

Build the fastest parallel-install debug APK for local testing:

```bash
cd /Users/hao.chen/工作文档/Work/readyou/ReadYou
./scripts/build-github-debug.sh
```

This defaults to `assembleGithubAiDebug`.

## Command Syntax

```bash
./scripts/build-github-debug.sh [fast-debug|release|<gradle-task>] [--profile full|1core]
```

Defaults:

- no arguments: `fast-debug --profile full`
- `fast-debug`: `assembleGithubAiDebug --profile full`
- `release`: `assembleGithubAiRelease --profile full`

## Explicit Modes

Build the real signed parallel-install release APK with full-machine resources:

```bash
./scripts/build-github-debug.sh release
```

Force fast debug explicitly:

```bash
./scripts/build-github-debug.sh fast-debug
```

Run release in low-load single-core mode:

```bash
./scripts/build-github-debug.sh release --profile 1core
```

## Build Another Variant Or Task

You can pass a Gradle task explicitly:

```bash
./scripts/build-github-debug.sh assembleGithubDebug --profile full
./scripts/build-github-debug.sh assembleGithubAiDebug --profile full
./scripts/build-github-debug.sh assembleGithubAiRelease --profile full
./scripts/build-github-debug.sh installGithubAiDebug --profile full
./scripts/build-github-debug.sh assembleGithubRelease --profile 1core
./scripts/build-github-debug.sh assembleGooglePlayRelease --profile full
```

## Requirements

The script expects:

- A usable JDK 21 environment
- Android SDK installed under `~/Library/Android/sdk`, or `ANDROID_SDK_ROOT` set manually
- Signing material for release builds

This repository already uses:

- `signature/keystore.properties`
- `signature/reader.keystore`

Without valid signing configuration, release builds such as `assembleGithubAiRelease` may fail.

## Full Profile

`--profile full` is the default for both `fast-debug` and `release`.

It is tuned to use the whole machine:

- detected logical CPU count from `sysctl`, `nproc`, `getconf`, or `NUMBER_OF_PROCESSORS`
- `--daemon`
- `--parallel`
- `--build-cache`
- `--max-workers=<detected logical CPUs>`
- Kotlin compiler daemon mode

Typical defaults on Apple Silicon M1:

- `fast-debug` => `assembleGithubAiDebug`
- `release` => `assembleGithubAiRelease`
- `--daemon`
- `--parallel`
- `--build-cache`
- `--max-workers=8`

You can override worker count if needed:

```bash
FAST_DEBUG_MAX_WORKERS=6 ./scripts/build-github-debug.sh
```

## Single-Core Profile

`--profile 1core` is the explicit low-load mode. It applies:

- `--no-daemon`
- `--max-workers=1`
- `-Dorg.gradle.workers.max=1`
- `-Dkotlin.compiler.execution.strategy=in-process`
- `-Djava.util.concurrent.ForkJoinPool.common.parallelism=1`
- `-XX:ActiveProcessorCount=1`

## Output Location

Release APK output:

```text
app/build/outputs/apk/githubAi/release/
```

Debug APK output:

```text
app/build/outputs/apk/githubAi/debug/
```

The script prints the exact APK path at the end of the build.

## Notes

- The script name is historical; its default behavior is now the parallel-install debug flavor.
- Release builds are slower than debug builds because they run shrinking, optimization, and signing steps.
- If you only need a local test APK, prefer the default `fast-debug` mode.
- If you want the real release APK but do not want to saturate the machine, use `release --profile 1core`.
