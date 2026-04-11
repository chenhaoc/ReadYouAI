# ReadYou Agent Guide

## Project Overview

- Project: `ReadYou`
- Type: Android RSS reader
- UI stack: Jetpack Compose
- Language: Kotlin
- Build system: Gradle with Kotlin DSL
- Main app module: `app`

## Repository Layout

- `app/`: Android application code, resources, Room schemas
- `docs/`: handoff notes, build docs, process docs
- `scripts/`: local build helpers
- `fastlane/`: release metadata and screenshots
- `signature/`: signing configuration files

## Important Build Facts

- Current version: `0.16.1.1`
- Current versionCode: `47`
- Default package id: `me.ash.reader`
- Parallel-install AI flavor package id: `me.ash.reader.ai`
- Parallel-install AI flavor label: `Read You AI`

## Preferred Local Build Commands

Use the helper script at:

```bash
./scripts/build-github-debug.sh
```

Supported form:

```bash
./scripts/build-github-debug.sh [fast-debug|release|<gradle-task>] [--profile full|1core]
```

Recommended commands:

- Fast local debug build:

```bash
./scripts/build-github-debug.sh
```

- Explicit fast debug:

```bash
./scripts/build-github-debug.sh fast-debug
```

- Real release build with full machine resources:

```bash
./scripts/build-github-debug.sh release
```

- Real release build with single-core low-load mode:

```bash
./scripts/build-github-debug.sh release --profile 1core
```

## Build Script Behavior

- Default mode is `fast-debug --profile full`
- `release` also defaults to `--profile full`
- `--profile full` uses detected logical CPU count
- `--profile 1core` forces single-core conservative execution
- The script prints the final APK path after a successful build

## Verification Commands

- Shell test for build script:

```bash
bash scripts/tests/build-github-debug-test.sh
```

- Shell syntax checks:

```bash
bash -n scripts/build-github-debug.sh
bash -n scripts/tests/build-github-debug-test.sh
```

## Working Conventions

- Prefer the `githubAi` flavor for local testing when side-by-side install is useful.
- Prefer `fast-debug` for feature verification.
- Use `release` only when a real signed APK is needed.
- Do not assume release builds should be single-core; single-core is opt-in.
- Check `docs/session-handoff-*.md` before continuing interrupted work.
- Write commit messages in Chinese.
- Prefer a short title plus a concise bullet list for commit bodies when summarizing multiple changes.
- Keep commit bullets focused on user-visible or workflow-relevant changes, not noisy edit inventory.

## Current High-Signal Files

- `app/build.gradle.kts`
- `scripts/build-github-debug.sh`
- `scripts/tests/build-github-debug-test.sh`
- `docs/build-script.md`
