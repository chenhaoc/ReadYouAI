#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
DEFAULT_ANDROID_SDK_ROOT="${HOME}/Library/Android/sdk"
DEBUG_TASK="assembleGithubAiDebug"
RELEASE_TASK="assembleGithubAiRelease"
DEBUG_GRADLE_JVMARGS="-Xmx4096M -Xms512m -XX:MaxMetaspaceSize=768m -Dkotlin.daemon.jvm.options=-Xmx2048M -XX:+HeapDumpOnOutOfMemoryError -XX:+UseParallelGC -Dfile.encoding=UTF-8"
RELEASE_GRADLE_JVMARGS="-Xmx8192M -Xms512m -XX:MaxMetaspaceSize=1g -Dkotlin.daemon.jvm.options=-Xmx8192M -XX:+HeapDumpOnOutOfMemoryError -XX:+UseParallelGC -Dfile.encoding=UTF-8"
LOCK_WAIT_SECONDS=5
LOCK_LOG_INTERVAL_SECONDS=30
DEFAULT_MODE="debug"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/build-github-debug.sh
  ./scripts/build-github-debug.sh debug
  ./scripts/build-github-debug.sh release
  ./scripts/build-github-debug.sh <gradle-task>

Notes:
  - The default task is assembleGithubAiDebug.
  - The script now serializes builds across all worktrees of the same repository.
  - Resource profiles such as --profile full|1core are no longer supported.
EOF
}

for arg in "$@"; do
  case "${arg}" in
    fast-debug)
      echo "The fast-debug alias is removed. Use debug or run the script without arguments." >&2
      exit 1
      ;;
    --profile|--profile=*)
      echo "Resource profiles are removed. Run one repository build at a time and let it finish." >&2
      exit 1
      ;;
  esac
done

if [[ $# -gt 1 ]]; then
  usage >&2
  exit 1
fi

if [[ $# -eq 1 ]]; then
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    debug)
      REQUESTED_TASK="${DEBUG_TASK}"
      ;;
    release)
      REQUESTED_TASK="${RELEASE_TASK}"
      ;;
    *)
      REQUESTED_TASK="$1"
      ;;
  esac
else
  REQUESTED_TASK="${DEBUG_TASK}"
fi

if [[ "${REQUESTED_TASK}" == *Release* ]]; then
  BUILD_MODE="release"
else
  BUILD_MODE="${DEFAULT_MODE}"
fi

detect_logical_cpus() {
  local count=""
  if command -v sysctl >/dev/null 2>&1; then
    count="$(sysctl -n hw.logicalcpu 2>/dev/null || true)"
  fi
  if [[ -z "${count}" ]] && command -v nproc >/dev/null 2>&1; then
    count="$(nproc 2>/dev/null || true)"
  fi
  if [[ -z "${count}" ]] && command -v getconf >/dev/null 2>&1; then
    count="$(getconf _NPROCESSORS_ONLN 2>/dev/null || true)"
  fi
  if [[ -z "${count}" ]] && [[ -n "${NUMBER_OF_PROCESSORS:-}" ]]; then
    count="${NUMBER_OF_PROCESSORS}"
  fi
  if [[ ! "${count}" =~ ^[0-9]+$ ]] || [[ "${count}" -lt 1 ]]; then
    count=4
  fi
  printf '%s' "${count}"
}

stat_mtime() {
  local file_path="$1"
  if stat -f '%m' "${file_path}" >/dev/null 2>&1; then
    stat -f '%m' "${file_path}"
  else
    stat -c '%Y' "${file_path}"
  fi
}

find_latest_apk() {
  local search_root="$1"
  local apk_files=()
  local latest_apk=""
  local latest_mtime=""
  local candidate_mtime=""
  local file_path=""

  while IFS= read -r -d '' file_path; do
    apk_files+=("${file_path}")
  done < <(find "${search_root}" -type f -name '*.apk' -print0)

  if [[ "${#apk_files[@]}" -eq 0 ]]; then
    return 1
  fi

  latest_apk="${apk_files[0]}"
  latest_mtime="$(stat_mtime "${latest_apk}")"

  for file_path in "${apk_files[@]:1}"; do
    candidate_mtime="$(stat_mtime "${file_path}")"
    if [[ "${candidate_mtime}" -gt "${latest_mtime}" ]]; then
      latest_apk="${file_path}"
      latest_mtime="${candidate_mtime}"
    fi
  done

  printf '%s\n' "${latest_apk}"
}

resolve_git_common_dir() {
  local git_common_dir=""
  if git_common_dir="$(git -C "${ROOT_DIR}" rev-parse --git-common-dir 2>/dev/null)"; then
    if [[ "${git_common_dir}" != /* ]]; then
      printf '%s/%s\n' "${ROOT_DIR}" "${git_common_dir}"
    else
      printf '%s\n' "${git_common_dir}"
    fi
  else
    printf '%s/.git\n' "${ROOT_DIR}"
  fi
}

read_lock_value() {
  local file_path="$1"
  if [[ -f "${file_path}" ]]; then
    tr -d '\n' < "${file_path}"
  fi
}

remove_stale_lock_if_safe() {
  local lock_dir="$1"
  local lock_host
  local lock_pid

  lock_host="$(read_lock_value "${lock_dir}/host")"
  lock_pid="$(read_lock_value "${lock_dir}/pid")"

  if [[ -z "${lock_pid}" ]] || [[ -z "${lock_host}" ]]; then
    return 1
  fi

  if [[ "${lock_host}" != "${CURRENT_HOST}" ]]; then
    return 1
  fi

  if kill -0 "${lock_pid}" 2>/dev/null; then
    return 1
  fi

  echo "Removing stale repository build lock from pid ${lock_pid}."
  rm -rf "${lock_dir}"
  return 0
}

log_lock_status() {
  local lock_dir="$1"
  local lock_host
  local lock_pid
  local lock_task
  local lock_cwd
  local lock_started_at

  lock_host="$(read_lock_value "${lock_dir}/host")"
  lock_pid="$(read_lock_value "${lock_dir}/pid")"
  lock_task="$(read_lock_value "${lock_dir}/task")"
  lock_cwd="$(read_lock_value "${lock_dir}/cwd")"
  lock_started_at="$(read_lock_value "${lock_dir}/started_at")"

  if [[ -n "${lock_task}" ]]; then
    echo "Active task: ${lock_task}"
  fi
  if [[ -n "${lock_pid}" ]] || [[ -n "${lock_host}" ]]; then
    echo "Lock owner: pid=${lock_pid:-unknown} host=${lock_host:-unknown}"
  fi
  if [[ -n "${lock_cwd}" ]]; then
    echo "Lock worktree: ${lock_cwd}"
  fi
  if [[ -n "${lock_started_at}" ]]; then
    echo "Lock started at: ${lock_started_at}"
  fi
}

acquire_build_lock() {
  local waited_seconds=0

  while ! mkdir "${BUILD_LOCK_DIR}" 2>/dev/null; do
    if remove_stale_lock_if_safe "${BUILD_LOCK_DIR}"; then
      continue
    fi

    if [[ "${waited_seconds}" -eq 0 ]]; then
      echo "Another repository build is already running. Waiting for it to finish..."
      log_lock_status "${BUILD_LOCK_DIR}"
      echo "This repository serializes builds across all worktrees to protect incremental performance."
    elif (( waited_seconds % LOCK_LOG_INTERVAL_SECONDS == 0 )); then
      echo "Still waiting for the active repository build lock (${waited_seconds}s elapsed)..."
      log_lock_status "${BUILD_LOCK_DIR}"
    fi

    sleep "${LOCK_WAIT_SECONDS}"
    waited_seconds=$((waited_seconds + LOCK_WAIT_SECONDS))
  done

  printf '%s\n' "$$" > "${BUILD_LOCK_DIR}/pid"
  printf '%s\n' "${CURRENT_HOST}" > "${BUILD_LOCK_DIR}/host"
  printf '%s\n' "${REQUESTED_TASK}" > "${BUILD_LOCK_DIR}/task"
  printf '%s\n' "${ROOT_DIR}" > "${BUILD_LOCK_DIR}/cwd"
  printf '%s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')" > "${BUILD_LOCK_DIR}/started_at"
}

release_build_lock() {
  local lock_pid
  local lock_host

  if [[ -z "${BUILD_LOCK_DIR:-}" ]] || [[ ! -d "${BUILD_LOCK_DIR}" ]]; then
    return
  fi

  lock_pid="$(read_lock_value "${BUILD_LOCK_DIR}/pid")"
  lock_host="$(read_lock_value "${BUILD_LOCK_DIR}/host")"

  if [[ "${lock_pid}" == "$$" ]] && [[ "${lock_host}" == "${CURRENT_HOST}" ]]; then
    rm -rf "${BUILD_LOCK_DIR}"
  fi
}

if [[ -n "${READYOU_BUILD_MAX_WORKERS:-}" ]]; then
  WORKER_COUNT="${READYOU_BUILD_MAX_WORKERS}"
else
  WORKER_COUNT="$(detect_logical_cpus)"
fi

if [[ ! "${WORKER_COUNT}" =~ ^[0-9]+$ ]] || [[ "${WORKER_COUNT}" -lt 1 ]]; then
  echo "Invalid worker count: ${WORKER_COUNT}" >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "${DEFAULT_JAVA_HOME}" ]]; then
    export JAVA_HOME="${DEFAULT_JAVA_HOME}"
  elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$("/usr/libexec/java_home")"
  else
    echo "JAVA_HOME is not set and no usable JDK was found." >&2
    exit 1
  fi
fi

export PATH="${JAVA_HOME}/bin:${PATH}"

if ! command -v java >/dev/null 2>&1; then
  echo "java is not available. Set JAVA_HOME to a usable JDK first." >&2
  exit 1
fi

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
  if [[ -d "${DEFAULT_ANDROID_SDK_ROOT}" ]]; then
    export ANDROID_SDK_ROOT="${DEFAULT_ANDROID_SDK_ROOT}"
  else
    echo "ANDROID_SDK_ROOT is not set and ${DEFAULT_ANDROID_SDK_ROOT} does not exist." >&2
    exit 1
  fi
fi

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT}}"
export PATH="${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

LOCAL_PROPERTIES_PATH="${ROOT_DIR}/local.properties"
if [[ ! -f "${LOCAL_PROPERTIES_PATH}" ]]; then
  printf 'sdk.dir=%s\n' "${ANDROID_SDK_ROOT}" > "${LOCAL_PROPERTIES_PATH}"
fi

# Disable inherited proxy settings for Gradle. This avoids stale macOS proxy host/port
# entries breaking dependency resolution when no local proxy is actually running.
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy NO_PROXY no_proxy

if command -v scutil >/dev/null 2>&1; then
  CURRENT_HOST="$(scutil --get ComputerName 2>/dev/null || true)"
fi
CURRENT_HOST="${CURRENT_HOST:-$(hostname -s 2>/dev/null || hostname)}"
GIT_COMMON_DIR="$(resolve_git_common_dir)"
BUILD_LOCK_DIR="${GIT_COMMON_DIR}/readyou-android-build.lock"
trap release_build_lock EXIT INT TERM
acquire_build_lock

if [[ "${BUILD_MODE}" == "release" ]]; then
  GRADLE_DAEMON_JVMARGS="${RELEASE_GRADLE_JVMARGS}"
else
  GRADLE_DAEMON_JVMARGS="${DEBUG_GRADLE_JVMARGS}"
fi

GRADLE_FLAGS=(
  "--daemon"
  "--build-cache"
  "--max-workers=${WORKER_COUNT}"
  "-Dorg.gradle.jvmargs=${GRADLE_DAEMON_JVMARGS}"
  "${REQUESTED_TASK}"
)
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.workers.max=${WORKER_COUNT} -Dkotlin.compiler.execution.strategy=daemon -Djava.net.useSystemProxies=false -Dorg.gradle.caching=true"

cd "${ROOT_DIR}"

echo "Build mode=${BUILD_MODE}"
echo "Worker count=${WORKER_COUNT}"
echo "JAVA_HOME=${JAVA_HOME}"
echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}"
echo "Build lock=${BUILD_LOCK_DIR}"
echo "GRADLE_DAEMON_JVMARGS=${GRADLE_DAEMON_JVMARGS}"
echo "Gradle task=${REQUESTED_TASK}"

./gradlew "${GRADLE_FLAGS[@]}"

if ! APK_PATH="$(find_latest_apk "${ROOT_DIR}/app/build/outputs/apk")"; then
  echo "Build finished but no APK was found under app/build/outputs/apk." >&2
  exit 1
fi

echo
echo "APK: ${APK_PATH}"
