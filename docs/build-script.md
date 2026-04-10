# Build Script Guide

This repository includes a one-command build script at:

```bash
./scripts/build-github-debug.sh
```

Despite the file name, the script now builds the `githubRelease` variant by default.

## What It Does

The script is designed for local release builds that should be installable on a normal Android phone.

It does the following:

1. Detects `JAVA_HOME`, defaulting to Homebrew OpenJDK 21 on Apple Silicon macOS.
2. Detects `ANDROID_SDK_ROOT`, defaulting to `~/Library/Android/sdk`.
3. Creates `local.properties` automatically if it does not exist.
4. Disables inherited proxy variables to avoid broken local proxy settings affecting Gradle dependency downloads.
5. Forces the build into single-core mode.
6. Runs Gradle with `assembleGithubRelease` by default.
7. Prints the final APK path after a successful build.

## Default Usage

Build the signed release APK:

```bash
cd /Users/hao.chen/工作文档/Work/readyou/ReadYou
./scripts/build-github-debug.sh
```

## Build Another Variant

You can pass a Gradle task explicitly:

```bash
./scripts/build-github-debug.sh assembleGithubDebug
./scripts/build-github-debug.sh assembleGithubRelease
./scripts/build-github-debug.sh assembleGooglePlayRelease
```

## Requirements

The script expects:

- A usable JDK 21 environment
- Android SDK installed under `~/Library/Android/sdk`, or `ANDROID_SDK_ROOT` set manually
- Signing material for release builds

This repository already uses:

- `signature/keystore.properties`
- `signature/reader.keystore`

Without valid signing configuration, `assembleGithubRelease` may fail.

## Single-Core Constraint

The script is configured to minimize machine load and stay on a single CPU core as much as Gradle allows. It applies:

- `--max-workers=1`
- `-Dorg.gradle.workers.max=1`
- `-Dkotlin.compiler.execution.strategy=in-process`
- `-Djava.util.concurrent.ForkJoinPool.common.parallelism=1`
- `-XX:ActiveProcessorCount=1`

## Output Location

Release APK output:

```text
app/build/outputs/apk/github/release/
```

Debug APK output:

```text
app/build/outputs/apk/github/debug/
```

The script prints the exact APK path at the end of the build.

## Notes

- The script name is historical; its default behavior is now release-oriented.
- Release builds are slower than debug builds because they run shrinking, optimization, and signing steps.
- If you need a fast verification build, pass `assembleGithubDebug` explicitly.
