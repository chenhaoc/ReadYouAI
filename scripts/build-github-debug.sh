#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
DEFAULT_ANDROID_SDK_ROOT="${HOME}/Library/Android/sdk"
GRADLE_TASK="${1:-assembleGithubRelease}"
GRADLE_DAEMON_JVMARGS="-Xmx8192M -Xms512m -XX:MaxMetaspaceSize=1g -Dkotlin.daemon.jvm.options=-Xmx8192M -XX:+HeapDumpOnOutOfMemoryError -XX:+UseParallelGC -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=1"

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
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -XX:ActiveProcessorCount=1"

LOCAL_PROPERTIES_PATH="${ROOT_DIR}/local.properties"
if [[ ! -f "${LOCAL_PROPERTIES_PATH}" ]]; then
  printf 'sdk.dir=%s\n' "${ANDROID_SDK_ROOT}" > "${LOCAL_PROPERTIES_PATH}"
fi

# Disable inherited proxy settings for Gradle. This avoids stale macOS proxy host/port
# entries breaking dependency resolution when no local proxy is actually running.
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy NO_PROXY no_proxy

GRADLE_FLAGS=(
  "--no-daemon"
  "--max-workers=1"
  "-Dorg.gradle.jvmargs=${GRADLE_DAEMON_JVMARGS}"
  "${GRADLE_TASK}"
)

export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process -Djava.util.concurrent.ForkJoinPool.common.parallelism=1 -Djava.net.useSystemProxies=false"

cd "${ROOT_DIR}"

echo "JAVA_HOME=${JAVA_HOME}"
echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}"
echo "JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}"
echo "GRADLE_DAEMON_JVMARGS=${GRADLE_DAEMON_JVMARGS}"
echo "Gradle task=${GRADLE_TASK}"

./gradlew "${GRADLE_FLAGS[@]}"

APK_PATH="$(find "${ROOT_DIR}/app/build/outputs/apk" -type f -name '*.apk' -print0 | xargs -0 ls -t 2>/dev/null | head -n 1)"
if [[ -z "${APK_PATH}" ]]; then
  echo "Build finished but no APK was found under app/build/outputs/apk." >&2
  exit 1
fi

echo
echo "APK: ${APK_PATH}"
