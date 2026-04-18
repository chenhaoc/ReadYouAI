#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SOURCE_SCRIPT="${REPO_ROOT}/scripts/build-github-debug.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

PROJECT_DIR="${TMP_DIR}/project"
mkdir -p "${PROJECT_DIR}/scripts" "${PROJECT_DIR}/app/build/outputs/apk/githubAi/debug"
cp "${SOURCE_SCRIPT}" "${PROJECT_DIR}/scripts/build-github-debug.sh"
chmod +x "${PROJECT_DIR}/scripts/build-github-debug.sh"
git -C "${TMP_DIR}" init -q "${PROJECT_DIR}"

mkdir -p "${PROJECT_DIR}/bin"
FAKE_SYSCTL="${PROJECT_DIR}/bin/sysctl"
cat > "${PROJECT_DIR}/bin/scutil" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == "--get" && "${2:-}" == "ComputerName" ]]; then
  printf 'test-host\n'
  exit 0
fi
exit 1
EOF
chmod +x "${PROJECT_DIR}/bin/scutil"
cat > "${PROJECT_DIR}/bin/nproc" <<'EOF'
#!/usr/bin/env bash
printf '8\n'
EOF
chmod +x "${PROJECT_DIR}/bin/nproc"
cat > "${FAKE_SYSCTL}" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == "-n" && "${2:-}" == "hw.logicalcpu" ]]; then
  echo 8
  exit 0
fi
echo "Unexpected sysctl args: $*" >&2
exit 1
EOF
chmod +x "${FAKE_SYSCTL}"

FAKE_JAVA_HOME="${PROJECT_DIR}/fake-java-home"
mkdir -p "${FAKE_JAVA_HOME}/bin"
cat > "${FAKE_JAVA_HOME}/bin/java" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "${FAKE_JAVA_HOME}/bin/java"

FAKE_ANDROID_SDK="${PROJECT_DIR}/fake-android-sdk"
mkdir -p "${FAKE_ANDROID_SDK}/platform-tools"
cat > "${FAKE_ANDROID_SDK}/platform-tools/adb" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "${FAKE_ANDROID_SDK}/platform-tools/adb"


cat > "${PROJECT_DIR}/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > gradle-args.log

TASK=""
for arg in "$@"; do
  case "$arg" in
    assembleGithubAiDebug|assembleGithubAiRelease)
      TASK="$arg"
      ;;
  esac
done

if [[ "$TASK" == "assembleGithubAiRelease" ]]; then
  mkdir -p app/build/outputs/apk/githubAi/release
  : > app/build/outputs/apk/githubAi/release/fake-release.apk
else
  mkdir -p app/build/outputs/apk/githubAi/debug
  : > app/build/outputs/apk/githubAi/debug/fake-debug.apk
fi
EOF
chmod +x "${PROJECT_DIR}/gradlew"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    echo "Expected to find: ${needle}" >&2
    echo "Actual: ${haystack}" >&2
    exit 1
  fi
}

assert_not_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" == *"${needle}"* ]]; then
    echo "Did not expect to find: ${needle}" >&2
    echo "Actual: ${haystack}" >&2
    exit 1
  fi
}

LOG_DIR="${PROJECT_DIR}/logs"
mkdir -p "${LOG_DIR}"

run_case() {
  local name="$1"
  shift

  (
    cd "${PROJECT_DIR}"
    export PATH="${FAKE_JAVA_HOME}/bin:${PROJECT_DIR}/bin:${FAKE_ANDROID_SDK}/platform-tools:${PATH}"
    export JAVA_HOME="${FAKE_JAVA_HOME}"
    export ANDROID_SDK_ROOT="${FAKE_ANDROID_SDK}"
    export ANDROID_HOME="${ANDROID_SDK_ROOT}"
    rm -f gradle-args.log
    ./scripts/build-github-debug.sh "$@" >"${LOG_DIR}/${name}.log" 2>&1
  )
}

run_case default
DEFAULT_ARGS="$(tr '\n' ' ' < "${PROJECT_DIR}/gradle-args.log")"
assert_contains "${DEFAULT_ARGS}" "assembleGithubAiDebug"
assert_contains "${DEFAULT_ARGS}" "--daemon"
assert_contains "${DEFAULT_ARGS}" "--build-cache"
assert_contains "${DEFAULT_ARGS}" "--max-workers=8"
assert_not_contains "${DEFAULT_ARGS}" "--parallel"

run_case debug_alias debug
DEBUG_ALIAS_ARGS="$(tr '\n' ' ' < "${PROJECT_DIR}/gradle-args.log")"
assert_contains "${DEBUG_ALIAS_ARGS}" "assembleGithubAiDebug"
assert_contains "${DEBUG_ALIAS_ARGS}" "--daemon"
assert_contains "${DEBUG_ALIAS_ARGS}" "--build-cache"
assert_not_contains "${DEBUG_ALIAS_ARGS}" "--parallel"

(
  cd "${PROJECT_DIR}"
  if ./scripts/build-github-debug.sh fast-debug >"${LOG_DIR}/fast_debug_error.log" 2>&1; then
    echo "Expected fast-debug alias invocation to fail." >&2
    exit 1
  fi
)
FAST_DEBUG_ERROR_LOG="$(cat "${LOG_DIR}/fast_debug_error.log")"
assert_contains "${FAST_DEBUG_ERROR_LOG}" "fast-debug alias is removed"

run_case release_alias release
RELEASE_ALIAS_ARGS="$(tr '\n' ' ' < "${PROJECT_DIR}/gradle-args.log")"
assert_contains "${RELEASE_ALIAS_ARGS}" "assembleGithubAiRelease"
assert_contains "${RELEASE_ALIAS_ARGS}" "--daemon"
assert_contains "${RELEASE_ALIAS_ARGS}" "--max-workers=8"

(
  cd "${PROJECT_DIR}"
  if ./scripts/build-github-debug.sh release --profile 1core >"${LOG_DIR}/profile_error.log" 2>&1; then
    echo "Expected --profile invocation to fail." >&2
    exit 1
  fi
)
PROFILE_ERROR_LOG="$(cat "${LOG_DIR}/profile_error.log")"
assert_contains "${PROFILE_ERROR_LOG}" "Resource profiles are removed"

run_case explicit_task compileGithubAiDebugKotlin
EXPLICIT_TASK_ARGS="$(tr '\n' ' ' < "${PROJECT_DIR}/gradle-args.log")"
assert_contains "${EXPLICIT_TASK_ARGS}" "compileGithubAiDebugKotlin"
assert_contains "${EXPLICIT_TASK_ARGS}" "--max-workers=8"

CURRENT_HOST="test-host"
LOCK_DIR="${PROJECT_DIR}/.git/readyou-android-build.lock"
mkdir -p "${LOCK_DIR}"
printf '%s\n' "${CURRENT_HOST}" > "${LOCK_DIR}/host"
printf '%s\n' "assembleGithubAiDebug" > "${LOCK_DIR}/task"
printf '%s\n' "/tmp/lock-holder" > "${LOCK_DIR}/cwd"
printf '%s\n' "2026-04-18 00:00:00 +0800" > "${LOCK_DIR}/started_at"
(
  sleep 2
  rm -rf "${LOCK_DIR}"
) &
LOCK_HOLDER_PID=$!
printf '%s\n' "${LOCK_HOLDER_PID}" > "${LOCK_DIR}/pid"
run_case waiting_for_lock
wait "${LOCK_HOLDER_PID}"
WAITING_LOG="$(cat "${LOG_DIR}/waiting_for_lock.log")"
assert_contains "${WAITING_LOG}" "Another repository build is already running. Waiting for it to finish"
assert_contains "${WAITING_LOG}" "Active task: assembleGithubAiDebug"

echo "build-github-debug.sh tests passed"
