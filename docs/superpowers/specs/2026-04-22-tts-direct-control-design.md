# TTS 直接发声与媒体播放归属方案

## 背景

目标是在保留 `TextToSpeech.speak(...)` 直接朗读链路的前提下，让通知栏和蓝牙耳机媒体按键稳定控制当前 TTS 播放。

早期方案只依赖 `TtsPlaybackService + MediaSessionCompat + audio focus`。这能让系统 UI 看到 ReadYou 的媒体会话，但在 MIUI 等 ROM 上并不总能让蓝牙媒体键路由到 ReadYou：真实发声进程可能是系统 TTS 引擎（例如 `com.xiaomi.mibrain.speech`），系统仍可能把按键交给上一个真实媒体应用。

因此最终方案收敛为：**直接 TTS 发声 + 静音媒体播放归属锚点 + MediaSession 回调控制 TTS**。

## 最终方案

### 1. 保留直接 TTS 发声

- 继续由 `TextToSpeechManager` 调用 `TextToSpeech.speak(...)`
- 长文本仍按现有分段规则拆分，暂停、恢复、上一段、下一段都基于段索引工作
- 不使用 `synthesizeToFile(...) + MediaPlayer` 替换朗读链路，避免增加文件生成、起播延迟和拖动复杂度

### 2. 用静音媒体锚点拿到播放归属

- `TtsPlaybackService` 在 TTS 处于 `Preparing` 或 `Reading` 时启动一个 0 音量循环 `AudioTrack`
- `AudioTrack` 使用和 TTS 一致的 `AudioAttributes.USAGE_MEDIA + CONTENT_TYPE_SPEECH`
- 这个锚点不负责发声，只用于让系统把 ReadYou 识别为当前媒体播放来源
- TTS 暂停或服务销毁时，锚点暂停或释放，避免长期占用播放状态

### 3. 用 MediaSession 承接控制回调

- `TtsPlaybackService` 继续作为前台 `mediaPlayback` Service
- 服务持有 `MediaSessionCompat` 和通知栏控制按钮
- Service 在收到 `ACTION_MEDIA_BUTTON` 或 MediaSession 回调后，把播放、暂停、上一条、下一条、seek 等控制转给 `TtsQueueController`

### 4. 显式声明媒体属性和焦点

- TTS 输出和静音锚点都使用 `USAGE_MEDIA + CONTENT_TYPE_SPEECH`
- `MediaSessionCompat` 声明本地 `STREAM_MUSIC`
- 播放和准备播放时申请 audio focus，丢失焦点时暂停当前 TTS

### 5. 隔离 TTS 播放会话

- `TextToSpeechManager` 为每轮朗读维护 `playbackGeneration`
- 新朗读开始或 `stop()` 时递增 generation，旧 utterance 的 `onStart`、`onDone`、`onError` 回调若晚到会被丢弃
- 这层保护不负责耳机按键路由，只防止旧 TTS 回调污染新播放状态、进度、错误和队列推进

### 6. 处理冷启动恢复时序

- `TtsQueueController` 提供 restore 完成信号
- 当媒体键在队列 restore 前到达时，Service 先等待 restore 完成，再把按键分发给 `MediaSession`
- 避免冷启动时媒体键因为当前文章尚未恢复而直接空转

## 改动范围

- `app/src/main/java/me/ash/reader/infrastructure/android/TtsPlaybackService.kt`
- `app/src/main/java/me/ash/reader/infrastructure/android/TextToSpeech.kt`
- `app/src/main/java/me/ash/reader/infrastructure/android/ttsqueue/TtsQueueController.kt`
- `app/src/main/AndroidManifest.xml`
- 最小单测

## 已知边界

- 静音媒体锚点只负责媒体播放归属，不改变实际 TTS 发声路径
- 这套方案比完整改造为文件合成播放器轻，但比纯 MediaSession 更能稳定获得蓝牙媒体键路由
- ROM 仍可能有自定义蓝牙/媒体策略，但 ReadYou 已主动持有媒体播放来源，避免依赖“上一个媒体应用”或系统 TTS 引擎归属
- 当前不扩展自定义耳机多击语义，只复用系统媒体键语义

## 验证

- `./gradlew :app:compileGithubAiDebugKotlin`
- `./gradlew :app:testGithubAiDebugUnitTest --tests "me.ash.reader.infrastructure.android.TtsPlaybackServiceMediaSessionTest" --tests "me.ash.reader.infrastructure.android.ttsqueue.TtsQueueControllerTest.awaitRestore_completes_after_snapshot_state_is_loaded"`
- `./scripts/build-github-debug.sh debug`
- 真机蓝牙耳机播放/暂停控制验证
- 需要排查路由时，使用 `adb shell dumpsys media_session` 和 `adb logcat` 检查媒体键是否仍被发给其他应用
