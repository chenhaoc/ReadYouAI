# ReadYouAI 本地构建说明

本仓库保留一个统一的本地构建脚本：

```bash
./scripts/build-github-debug.sh
```

它现在的职责很收敛：

1. 优先服务 `githubAiDebug` 的本地增量编译
2. 在同一仓库的所有 worktree 之间串行化构建
3. 统一处理 `JAVA_HOME`、`ANDROID_SDK_ROOT`、`local.properties`
4. 成功后输出最终 APK 路径

## 为什么保留这个脚本

在 Apple Silicon Mac 上，单次全量 Android 构建本来就不快；如果多个 worktree 同时编译，会显著拖慢整体速度。相比“再起一个新构建”，更高效的做法通常是：

- 优先走已有增量状态
- 同一时刻只跑一个构建
- 已经有构建在执行时直接等待，而不是 kill 掉重跑

因此脚本的核心价值不再是资源档位切换，而是提供一个统一的、带仓库级构建锁的入口。

## 默认用法

最常用的是直接构建本地调试 APK：

```bash
./scripts/build-github-debug.sh
```

这会默认执行：

```bash
assembleGithubAiDebug
```

## 支持的调用方式

```bash
./scripts/build-github-debug.sh
./scripts/build-github-debug.sh debug
./scripts/build-github-debug.sh release
./scripts/build-github-debug.sh <gradle-task>
```

约定如下：

- 无参数：执行 `assembleGithubAiDebug`
- `debug`：映射到 `assembleGithubAiDebug`
- `release`：映射到 `assembleGithubAiRelease`
- 其他单个参数：按显式 Gradle task 处理

常见示例：

```bash
./scripts/build-github-debug.sh
./scripts/build-github-debug.sh release
./scripts/build-github-debug.sh :app:compileGithubAiDebugKotlin
./scripts/build-github-debug.sh testGithubAiDebugUnitTest
```

## 不再支持的旧参数

以下旧参数已经移除：

```bash
--profile full
--profile 1core
```

原因是它们更像“资源调参入口”，并没有解决当前这台机器最关键的问题：同一仓库多构建并发导致的整体变慢。

## 串行构建锁

脚本会在仓库公共 `.git` 目录下创建一把锁，因此：

- 多个 worktree 会共享同一把锁
- 如果另一个构建已经在运行，当前命令会等待
- 等待期间会打印当前持锁任务、worktree 和开始时间
- 若发现同机死锁进程残留，会自动清理陈旧锁

这条规则的目的很明确：

- 不要同时编译两个 worktree
- 不要因为等了一会儿就 kill 掉正在跑的构建
- 优先复用 daemon、cache 和增量编译结果

## 环境要求

脚本会自动补足以下环境：

- `JAVA_HOME`
  - 优先使用 Homebrew OpenJDK 21
- `ANDROID_SDK_ROOT`
  - 默认使用 `~/Library/Android/sdk`
- `local.properties`
  - 若缺失则自动生成

同时会清理继承下来的代理变量，避免无效代理影响 Gradle 解析依赖。

## 输出位置

Debug APK：

```text
app/build/outputs/apk/githubAi/debug/
```

Release APK：

```text
app/build/outputs/apk/githubAi/release/
```

脚本完成后会打印实际 APK 路径。

## 建议工作流

- 日常开发优先使用默认命令或 `compileGithubAiDebugKotlin`
- 以 `githubAiDebug` 的增量构建为主，不要动不动切到 release
- 只有在确实需要签名包时才跑 `release`
- 如果已经有一个构建在跑，等它结束，通常比重新起一个新构建更快
