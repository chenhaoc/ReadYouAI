# ReadYouAI

<p align="center">
  <img src="docs/branding/readyouai-icon.png" alt="ReadYouAI icon" width="168">
</p>

[ReadYou](https://github.com/ReadYouApp/ReadYou) 是一个以 Material You 风格呈现的 Android RSS 阅读器，支持订阅管理、阅读、朗读和多种数据源接入。

ReadYouAI 是基于 ReadYou 的个人增强版，当前重点增强 AI 阅读能力、TTS 播放体验，以及整体阅读交互体验。

当前默认分支是 `main-custom`，上游同步基线当前对应 `ReadYou 0.16.1`。

完整版本记录见 [docs/releases/0.16.1-custom.md](docs/releases/0.16.1-custom.md)。

## 这个分支相对原版增加了什么

### AI 阅读助手

- 实现文章 AI 摘要，可在阅读页直接查看中文结构化摘要，并快速跳转到摘要位置
- 实现自动摘要，可按订阅源开启，新文章同步后自动生成摘要，历史未读文章也可补齐摘要
- 实现阅读页 AI 问答，可围绕当前文章继续追问，并查看带格式的回复内容
- 支持 AI 问答联网搜索，并可在设置页直接测试当前配置是否支持联网搜索
- 实现订阅级文章翻译，文章列表和阅读页都可以展示标题翻译、正文翻译和翻译预览
- 支持多套 AI 配置的新增、复制、切换和测试，并集中管理服务地址、模型、API Key 与提示词

### 听读与播放

- 实现 TTS 播放列表，阅读页、信息流页和订阅页都可以加入队列或立即播放
- 实现全文模式和摘要模式，既可以听完整文章，也可以按分组和订阅源生成摘要收听列表
- 实现 AI 推荐摘要播放列表，可从候选文章中挑选更适合收听的内容
- 优化播放控制，支持队列排序、上一条/下一条、播放进度、定时播放和模态列表
- 支持悬浮播放器、通知栏和耳机媒体键控制播放
- 支持在播放卡片和通知栏直接切换当前文章收藏状态

### 阅读体验

- 支持在 WebView 和 native 阅读模式中显示内联摘要卡片
- 支持阅读页长按打开原文
- 支持显示字数统计和阅读时长估算
- 支持在订阅分组中显示文章数量
- 优化网页内容清洗，避免嵌入视频等控件文字混入正文和朗读内容

### 数据与设置

- 实现应用配置备份与恢复，支持导出和导入设置、账号、AI 配置和订阅源信息
- 优化设置页结构，集中管理 AI 摘要、翻译、问答、后台摘要和播放相关选项
- 增加提示与支持页面，用于查看版本能力、更新入口、开源许可和支持入口
- 提供 `githubAi` 发布包，可与原版 ReadYou 并行安装

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
