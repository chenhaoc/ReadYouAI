# Build Script Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the local Android build script so it supports `fast-debug` and `release` modes with explicit `full` and `1core` resource profiles, defaulting both modes to full-machine usage on Apple Silicon.

**Architecture:** Keep a single entry script at `scripts/build-github-debug.sh`, but make argument parsing explicit: mode and Gradle task selection first, then a profile layer that computes worker counts and JVM flags. Protect behavior with lightweight shell-level tests that fake `gradlew`, then refresh the user-facing guide to match the new command surface.

**Tech Stack:** Bash, Gradle wrapper invocation, macOS `sysctl`, repository docs in Markdown

---

### Task 1: Expand Script-Level Tests First

**Files:**
- Modify: `/Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/tests/build-github-debug-test.sh`

- [ ] **Step 1: Write the failing test cases for the new CLI contract**

Add assertions for:

```bash
run_case default
assert_contains "${DEFAULT_ARGS}" "assembleGithubAiDebug"
assert_contains "${DEFAULT_ARGS}" "--max-workers=8"

run_case release_full release
assert_contains "${RELEASE_FULL_ARGS}" "assembleGithubAiRelease"
assert_contains "${RELEASE_FULL_ARGS}" "--max-workers=8"
assert_contains "${RELEASE_FULL_ARGS}" "--daemon"

run_case release_1core release --profile 1core
assert_contains "${RELEASE_1CORE_ARGS}" "assembleGithubAiRelease"
assert_contains "${RELEASE_1CORE_ARGS}" "--max-workers=1"
assert_contains "${RELEASE_1CORE_ARGS}" "--no-daemon"

run_case explicit_task installGithubAiDebug --profile full
assert_contains "${EXPLICIT_TASK_ARGS}" "installGithubAiDebug"
assert_contains "${EXPLICIT_TASK_ARGS}" "--max-workers=8"
```

- [ ] **Step 2: Run the shell test to verify it fails against the current script**

Run:

```bash
bash /Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/tests/build-github-debug-test.sh
```

Expected:

```text
Expected to find: --max-workers=8
```

or another assertion failure proving the old script does not yet implement the new profile rules.

- [ ] **Step 3: Make the test harness deterministic for CPU detection**

Update the test harness so the copied script sees a deterministic CPU count:

```bash
FAKE_SYSCTL="${PROJECT_DIR}/bin/sysctl"
mkdir -p "${PROJECT_DIR}/bin"
cat > "${FAKE_SYSCTL}" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == "-n" && "${2:-}" == "hw.logicalcpu" ]]; then
  echo 8
  exit 0
fi
exec /usr/sbin/sysctl "$@"
EOF
chmod +x "${FAKE_SYSCTL}"
export PATH="${PROJECT_DIR}/bin:${PATH}"
```

- [ ] **Step 4: Re-run the test and confirm it still fails for the right reason**

Run:

```bash
bash /Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/tests/build-github-debug-test.sh
```

Expected: fail on new behavior assertions, not on missing `sysctl` or environment setup.

- [ ] **Step 5: Commit the red test change**

```bash
git -C /Users/hao.chen/工作文档/Work/readyou/ReadYou add scripts/tests/build-github-debug-test.sh
git -C /Users/hao.chen/工作文档/Work/readyou/ReadYou commit -m "test(build): cover build script resource profiles"
```

### Task 2: Implement Mode And Profile Parsing In The Build Script

**Files:**
- Modify: `/Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/build-github-debug.sh`
- Modify: `/Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/tests/build-github-debug-test.sh`

- [ ] **Step 1: Add positional mode parsing plus `--profile` flag parsing**

Refactor the top of the script so it parses one positional mode-or-task argument and one optional profile flag:

```bash
MODE_OR_TASK="fast-debug"
PROFILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile)
      PROFILE="${2:-}"
      shift 2
      ;;
    *)
      if [[ "${MODE_OR_TASK}" == "fast-debug" ]]; then
        MODE_OR_TASK="$1"
      else
        echo "Unexpected argument: $1" >&2
        exit 1
      fi
      shift
      ;;
  esac
done
```

- [ ] **Step 2: Add macOS logical CPU detection and normalized profile selection**

Use `sysctl` with a safe fallback:

```bash
detect_logical_cpu_count() {
  local cpu_count
  cpu_count="$(sysctl -n hw.logicalcpu 2>/dev/null || true)"
  if [[ -z "${cpu_count}" ]]; then
    cpu_count="8"
  fi
  echo "${cpu_count}"
}

FULL_PROFILE_WORKERS="$(detect_logical_cpu_count)"
RESOURCE_PROFILE="${PROFILE:-full}"
```

- [ ] **Step 3: Compute build mode, Gradle task, and worker count from the parsed inputs**

Keep inference rules aligned with the spec:

```bash
case "${MODE_OR_TASK}" in
  fast-debug)
    BUILD_MODE="fast-debug"
    GRADLE_TASK="assembleGithubAiDebug"
    ;;
  release)
    BUILD_MODE="release"
    GRADLE_TASK="assembleGithubAiRelease"
    ;;
  *)
    GRADLE_TASK="${MODE_OR_TASK}"
    if [[ "${GRADLE_TASK}" == *Release* ]]; then
      BUILD_MODE="release"
    else
      BUILD_MODE="fast-debug"
    fi
    ;;
esac

if [[ "${RESOURCE_PROFILE}" == "full" ]]; then
  WORKER_COUNT="${FULL_PROFILE_WORKERS}"
else
  WORKER_COUNT="1"
fi
```

- [ ] **Step 4: Implement `fast-debug + full` and `release + full` with full-resource flags**

Use one full-resource path for both modes:

```bash
GRADLE_FLAGS=(
  "--daemon"
  "--parallel"
  "--build-cache"
  "--max-workers=${WORKER_COUNT}"
  "-Dorg.gradle.jvmargs=${GRADLE_DAEMON_JVMARGS}"
  "${GRADLE_TASK}"
)

export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.workers.max=${WORKER_COUNT} -Dkotlin.compiler.execution.strategy=daemon -Djava.util.concurrent.ForkJoinPool.common.parallelism=${WORKER_COUNT} -Djava.net.useSystemProxies=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true"
```

Make sure `release --profile full` does **not** append `ActiveProcessorCount=1`.

- [ ] **Step 5: Keep `1core` as the explicit low-load path**

Only this branch should force single-core execution:

```bash
if [[ "${RESOURCE_PROFILE}" == "1core" ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -XX:ActiveProcessorCount=1"
  GRADLE_FLAGS=(
    "--no-daemon"
    "--max-workers=1"
    "-Dorg.gradle.jvmargs=${RELEASE_GRADLE_JVMARGS}"
    "${GRADLE_TASK}"
  )
fi
```

- [ ] **Step 6: Improve script output so the chosen profile is visible**

Print:

```bash
echo "Build mode=${BUILD_MODE}"
echo "Resource profile=${RESOURCE_PROFILE}"
echo "Worker count=${WORKER_COUNT}"
echo "Gradle task=${GRADLE_TASK}"
```

- [ ] **Step 7: Run the shell tests and verify they now pass**

Run:

```bash
bash /Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/tests/build-github-debug-test.sh
```

Expected:

```text
build-github-debug.sh tests passed
```

- [ ] **Step 8: Run syntax checks on the edited shell files**

Run:

```bash
bash -n /Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/build-github-debug.sh
bash -n /Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/tests/build-github-debug-test.sh
```

Expected: no output and exit code 0.

- [ ] **Step 9: Commit the implementation**

```bash
git -C /Users/hao.chen/工作文档/Work/readyou/ReadYou add scripts/build-github-debug.sh scripts/tests/build-github-debug-test.sh
git -C /Users/hao.chen/工作文档/Work/readyou/ReadYou commit -m "build: add full and single-core build profiles"
```

### Task 3: Update The Build Guide

**Files:**
- Modify: `/Users/hao.chen/工作文档/Work/readyou/ReadYou/docs/build-script.md`

- [ ] **Step 1: Rewrite the command summary around mode plus profile**

Update the guide so it documents:

```markdown
./scripts/build-github-debug.sh [fast-debug|release|<gradle-task>] [--profile full|1core]
```

and explains that both `fast-debug` and `release` now default to `full`.

- [ ] **Step 2: Add concrete usage examples for all supported release paths**

Include examples for:

```bash
./scripts/build-github-debug.sh
./scripts/build-github-debug.sh fast-debug
./scripts/build-github-debug.sh release
./scripts/build-github-debug.sh release --profile 1core
./scripts/build-github-debug.sh installGithubAiDebug --profile full
```

- [ ] **Step 3: Update the profile descriptions to match implementation**

Document:

- `full` uses detected logical CPU count on macOS
- M1 usually resolves to 8 logical cores
- `1core` is the explicit low-load option

- [ ] **Step 4: Review the guide for stale statements**

Remove or replace outdated claims such as:

```markdown
- release mode always stays on a single CPU core
- fast-debug defaults to 4 workers
```

The final doc should consistently say:

```markdown
- default: fast-debug + full
- release default: release + full
- single-core is opt-in through --profile 1core
```

- [ ] **Step 5: Run a focused diff review on the doc and script output examples**

Run:

```bash
git -C /Users/hao.chen/工作文档/Work/readyou/ReadYou diff -- docs/build-script.md scripts/build-github-debug.sh scripts/tests/build-github-debug-test.sh
```

Expected: the examples and printed script metadata agree on mode, profile, and worker count behavior.

- [ ] **Step 6: Commit the documentation update**

```bash
git -C /Users/hao.chen/工作文档/Work/readyou/ReadYou add docs/build-script.md
git -C /Users/hao.chen/工作文档/Work/readyou/ReadYou commit -m "docs: describe build script profiles"
```
