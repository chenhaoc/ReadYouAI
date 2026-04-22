# TTS 直接控制与耳机按键方案

## 背景

目标是在保留 `TextToSpeech.speak(...)` 直接朗读链路的前提下，让通知栏和蓝牙耳机媒体按键尽量稳定地控制当前 TTS 播放。

这次方案刻意不引入 `synthesizeToFile(...) + MediaPlayer`，原因是那条路径改动面更大，并且会明显影响起播和拖动时的响应体验。

## 最终方案

### 1. 保留直接 TTS 播放

- 继续由 `TextToSpeechManager` 调用 `TextToSpeech.speak(...)`
- 长文本仍按现有分段规则拆分，暂停、恢复、上一段、下一段都基于段索引工作
- 不新增本地音频文件和播放器链路

### 2. 用前台服务承接媒体控制

- `TtsPlaybackService` 继续作为前台 `mediaPlayback` Service
- 服务持有 `MediaSessionCompat` 和通知栏控制按钮
- Service 在收到 `ACTION_MEDIA_BUTTON` 后，把系统媒体键分发到现有 TTS 队列控制逻辑

### 3. 显式声明媒体属性和焦点

- TTS 输出使用 `AudioAttributes.USAGE_MEDIA + CONTENT_TYPE_SPEECH`
- `MediaSessionCompat` 声明本地 `STREAM_MUSIC`
- 播放和准备播放时申请 audio focus，丢失焦点时暂停当前 TTS

### 4. 处理冷启动恢复时序

- `TtsQueueController` 增加 restore 完成信号
- 当媒体键在队列 restore 前到达时，Service 先等待 restore 完成，再把按键分发给 `MediaSession`
- 避免冷启动时媒体键因为当前文章尚未恢复而直接空转

### 5. 提高 TTS 引擎可靠性

- 播放前等待 `TextToSpeech` 初始化完成
- 每次 `speak(...)` 都检查返回码
- 初始化失败或播放错误后，主动重建 `TextToSpeech` engine，避免坏实例长期滞留

## 改动范围

- `app/src/main/java/me/ash/reader/infrastructure/android/TtsPlaybackService.kt`
- `app/src/main/java/me/ash/reader/infrastructure/android/TextToSpeech.kt`
- `app/src/main/java/me/ash/reader/infrastructure/android/ttsqueue/TtsQueueController.kt`
- `app/src/main/AndroidManifest.xml`
- 最小单测

## 已知边界

- 由于真实发声仍由系统 TTS 引擎完成，媒体键路由稳定性仍受 ROM 和 TTS 引擎实现影响
- 这条方案比应用内自持播放器改动更小、响应更快，但媒体控制稳定性上限低于“应用自己播放音频”
- 当前只做最小可用和可靠性加固，不扩展自定义耳机多击语义

## 验证

- `./gradlew :app:compileGithubAiDebugKotlin`
- `./gradlew :app:testGithubAiDebugUnitTest --tests "me.ash.reader.infrastructure.android.TtsPlaybackServiceMediaSessionTest" --tests "me.ash.reader.infrastructure.android.ttsqueue.TtsQueueControllerTest.awaitRestore_completes_after_snapshot_state_is_loaded"`
- `./scripts/build-github-debug.sh`
