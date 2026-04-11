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

mkdir -p "${PROJECT_DIR}/bin"
FAKE_SYSCTL="${PROJECT_DIR}/bin/sysctl"
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
assert_contains "${DEFAULT_ARGS}" "--parallel"
assert_contains "${DEFAULT_ARGS}" "--build-cache"
assert_contains "${DEFAULT_ARGS}" "--max-workers=8"

run_case fast_full fast-debug --profile full
FAST_FULL_ARGS="$(tr '\n' ' ' < "${PROJECT_DIR}/gradle-args.log")"
assert_contains "${FAST_FULL_ARGS}" "assembleGithubAiDebug"
assert_contains "${FAST_FULL_ARGS}" "--daemon"
assert_contains "${FAST_FULL_ARGS}" "--parallel"
assert_contains "${FAST_FULL_ARGS}" "--build-cache"
assert_contains "${FAST_FULL_ARGS}" "--max-workers=8"

run_case release_full release
RELEASE_FULL_ARGS="$(tr '\n' ' ' < "${PROJECT_DIR}/gradle-args.log")"
assert_contains "${RELEASE_FULL_ARGS}" "assembleGithubAiRelease"
assert_contains "${RELEASE_FULL_ARGS}" "--daemon"
assert_contains "${RELEASE_FULL_ARGS}" "--max-workers=8"

run_case release_full_profile release --profile full
RELEASE_FULL_PROFILE_ARGS="$(tr '\n' ' ' < "${PROJECT_DIR}/gradle-args.log")"
assert_contains "${RELEASE_FULL_PROFILE_ARGS}" "assembleGithubAiRelease"
assert_contains "${RELEASE_FULL_PROFILE_ARGS}" "--daemon"
assert_contains "${RELEASE_FULL_PROFILE_ARGS}" "--max-workers=8"

run_case release_1core release --profile 1core
RELEASE_1CORE_ARGS="$(tr '\n' ' ' < "${PROJECT_DIR}/gradle-args.log")"
assert_contains "${RELEASE_1CORE_ARGS}" "assembleGithubAiRelease"
assert_contains "${RELEASE_1CORE_ARGS}" "--no-daemon"
assert_contains "${RELEASE_1CORE_ARGS}" "--max-workers=1"

run_case explicit_task installGithubAiDebug --profile full
EXPLICIT_TASK_ARGS="$(tr '\n' ' ' < "${PROJECT_DIR}/gradle-args.log")"
assert_contains "${EXPLICIT_TASK_ARGS}" "installGithubAiDebug"
assert_contains "${EXPLICIT_TASK_ARGS}" "--max-workers=8"

echo "build-github-debug.sh tests passed"
