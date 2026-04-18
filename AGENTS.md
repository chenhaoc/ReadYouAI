# ReadYouAI 协作说明

本文档面向在本仓库内协作的代码代理与自动化工具，约束默认工作方式、构建方式和本地定制发布流程。

## 项目概况

- 仓库：`ReadYouAI`
- 上游项目：`ReadYou`
- 类型：Android RSS 阅读器
- 技术栈：Kotlin、Jetpack Compose、Gradle Kotlin DSL
- 主模块：`app`
- 默认包名：`me.ash.reader`
- 并行安装 AI flavor 包名：`me.ash.reader.ai`

版本号、`versionCode`、APK 文件名和当前 custom 发布线，以 [app/build.gradle.kts](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/build.gradle.kts) 与 [0.16.1-custom.md](/Users/hao.chen/工作文档/Work/readyou/ReadYou/docs/releases/0.16.1-custom.md) 为准，不要在本文件里重复维护易过时的具体数字。

## 仓库结构

- `app/`：Android 应用代码、资源、Room schema、测试代码
- `docs/`：发布说明、handoff、设计文档、过程文档
- `scripts/`：本地构建辅助脚本
- `fastlane/`：发布元数据与截图
- `signature/`：签名配置

## 常用构建方式

优先使用仓库内脚本：

```bash
./scripts/build-github-debug.sh
```

支持的调用形式：

```bash
./scripts/build-github-debug.sh
./scripts/build-github-debug.sh debug
./scripts/build-github-debug.sh release
./scripts/build-github-debug.sh <gradle-task>
```

常用命令：

```bash
./scripts/build-github-debug.sh
./scripts/build-github-debug.sh debug
./scripts/build-github-debug.sh release
./scripts/build-github-debug.sh assembleGithubAiDebug
./scripts/build-github-debug.sh :app:compileGithubAiDebugKotlin
```

构建约定：

- 默认任务是 `assembleGithubAiDebug`
- `githubAi` flavor 用于本地并行安装测试
- 需要真实签名 APK 时再使用 `release`
- 同一时刻只允许一个仓库构建在运行，脚本会在所有 worktree 之间串行等待
- 默认优先复用增量编译结果，不要因为等待时间偏长就主动 kill 后重跑
- `--profile full|1core` 已废弃，不再支持

## 常用验证命令

```bash
bash scripts/tests/build-github-debug-test.sh
bash -n scripts/build-github-debug.sh
bash -n scripts/tests/build-github-debug-test.sh
./gradlew :app:compileGithubAiDebugKotlin
./gradlew :app:testGithubAiDebugUnitTest
```

做局部改动时，优先跑最小必要验证，不要默认触发整仓重构或全量 release 构建。
如果当前已经有构建在运行，等待它结束通常比重新起一个新构建更快。

## 提交规范

提交标题使用：

```text
type(scope): 中文摘要
```

提交正文使用中文简洁列表，例如：

```text
fix(tts): 改用模态播放列表并修复返回

- 将根级播放列表宿主切换为 ModalBottomSheet
- 删除无效的返回优先级抢占逻辑
- 调整播放列表边距与标题信息
```

约束：

- 一个独立功能尽量单独成提交
- 不要把无关 UI 调整、文档修改和功能修复混在同一提交
- 执行 `git commit` 前，先给出拟用的 commit msg，待用户确认后再提交
- 提交正文中的列表项必须使用真实换行，不要出现字面 `\n`
- 提交正文若使用列表，列表项之间不要插空行
- 需要整理私有分支历史时，优先重建干净提交链，而不是在脏历史上硬删

## custom 发布线规则

当前本地定制发布线采用 `0.16.1-custom.N` 形式递增维护。

发布一个新的 `custom.N` 时，至少同步处理：

1. 更新 [app/build.gradle.kts](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/build.gradle.kts) 中的 `versionName` 和 `versionCode`
2. 更新 [0.16.1-custom.md](/Users/hao.chen/工作文档/Work/readyou/ReadYou/docs/releases/0.16.1-custom.md) 对应 release note
3. 按功能粒度整理提交历史
4. 在 release note / 版本号就位后打对应 tag，例如 `0.16.1-custom.5`

如果本地重写过 `main-custom` 历史，后续推送远端时应使用 `git push --force-with-lease`，不要直接裸 `--force`。

## 协作约束

- 默认不要覆盖或清理用户已有的未提交改动
- 在删除分支、worktree、stash 或改写历史前，先确认它们是否只服务于当前任务
- 遇到本地私有分支时，可以整理历史；遇到共享分支时，优先追加新提交
- 文档应反映最终落地方案，不要保留已经证实无效的设计结论
- 中断续作时，优先检查 `docs/session-handoff-*.md`
- 不要同时在多个 worktree 中发起编译；需要构建时优先复用脚本内的全局串行锁

## 高信号文件

- [app/build.gradle.kts](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/build.gradle.kts)
- [scripts/build-github-debug.sh](/Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/build-github-debug.sh)
- [docs/build-script.md](/Users/hao.chen/工作文档/Work/readyou/ReadYou/docs/build-script.md)
- [0.16.1-custom.md](/Users/hao.chen/工作文档/Work/readyou/ReadYou/docs/releases/0.16.1-custom.md)
