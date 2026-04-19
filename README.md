# ReadYouAI

<p align="center">
  <img src="docs/branding/readyouai-icon.png" alt="ReadYouAI icon" width="168">
</p>

[ReadYou](https://github.com/ReadYouApp/ReadYou) 是一个以 Material You 风格呈现的 Android RSS 阅读器，支持订阅管理、阅读、朗读和多种数据源接入。

ReadYouAI 是基于 ReadYou 的个人增强版，当前重点增强 AI 阅读能力、TTS 播放体验，以及整体阅读交互体验。

当前默认分支是 `main-custom`，上游同步基线当前对应 `ReadYou 0.16.1`。

完整版本记录见 [docs/releases/0.16.1-custom.md](docs/releases/0.16.1-custom.md)。

## 这个分支相对原版增加了什么

### AI 能力

- AI 摘要
  - 基于上游 PR [#1210](https://github.com/ReadYouApp/ReadYou/pull/1210) “Add AI article summarization feature” 继续扩展，感谢 `jcrabapple` 提供原始 PR 与实现
  - 实现了内联摘要卡片、自动摘要和订阅级自动摘要
  - 实现了摘要跳转和返回阅读流程
- AI 翻译
  - 实现了订阅级翻译流程
  - 实现了列表页与阅读页之间的标题翻译、正文翻译、翻译预览和联动浏览体验
- AI 问答
  - 实现了阅读页 AI 问答入口、问答抽屉和会话历史保留
  - 支持基于 WebView 的 Markdown 回复渲染

### 阅读体验增强

- 增加阅读页长按打开原文
- 增加字数统计与阅读时长估算
- 增加订阅分组文章数量显示
- 清洗嵌入视频控件文字，减少阅读与朗读中的控件文案干扰，提升整体内容体验

### TTS 播放

- 实现“加入播放列表 / 立即播放”的阅读入口
- 实现跨订阅页、信息流页和阅读页的统一播放控制
- 实现完整的 TTS 播放列表、队列排序、上一条/下一条切换和当前进度展示
- 实现悬浮播放器、状态栏播放控件、当前时间与总时长显示
- 实现定时播放与模态播放列表

### 备份与设置

- 实现应用配置的备份与恢复能力
- 提供 AI 摘要、翻译、问答相关设置项、统一提示词配置与相关文案

## 分支说明

- `main-custom`
  你的默认使用分支，也是 GitHub 默认展示分支
- `main`
  保留用于跟踪上游主线和做同步对比

## 下载

- GitHub Releases: [chenhaoc/ReadYouAI/releases](https://github.com/chenhaoc/ReadYouAI/releases)
- GitHub Actions: [chenhaoc/ReadYouAI/actions](https://github.com/chenhaoc/ReadYouAI/actions)

如果某个版本还没有单独发布，可以直接使用仓库里的本地构建脚本生成 APK。

## 本地构建

最常用的是 `githubAiDebug`：

```bash
./scripts/build-github-debug.sh
```

需要签名的 `githubAiRelease` 时：

```bash
./scripts/build-github-debug.sh release
```

输出位置：

- Debug APK: `app/build/outputs/apk/githubAi/debug/`
- Release APK: `app/build/outputs/apk/githubAi/release/`

## 上游来源与致谢

- 上游项目： [ReadYouApp/ReadYou](https://github.com/ReadYouApp/ReadYou)
- 感谢原版 `ReadYou` 项目持续提供的产品设计、架构基础与开源维护，这个仓库的所有定制工作都建立在上游项目之上
- 这个仓库的目标不是替代上游，而是在上游基础上持续维护个人定制能力
- 当上游继续演进时，`main-custom` 会按需要做同步、挑拣和本地重整

## 许可证

沿用上游许可证： [GNU GPL v3.0](LICENSE)
