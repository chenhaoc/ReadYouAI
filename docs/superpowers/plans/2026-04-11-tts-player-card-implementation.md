# TTS Player Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fixed player card with weighted seek, stable queue ordering, per-article playback bookmarks, looped queue navigation, and direct queue entry from the article flow page.

**Architecture:** Move progress state from a single current-item model to a per-article bookmark model in the queue controller. Render one fixed control card above the queue list and reduce list items back to selection plus queue management. Keep queue controls reachable from both reading and article-flow contexts, and improve low-segment articles by splitting long speakable chunks before weighted progress is computed.

**Tech Stack:** Kotlin, Jetpack Compose, existing TTS queue controller, existing adaptive reading navigation.

---

### Task 1: Per-article bookmarks

**Files:**
- Modify: `app/src/main/java/me/ash/reader/infrastructure/android/ttsqueue/TtsQueueController.kt`
- Modify: `app/src/main/java/me/ash/reader/infrastructure/android/ttsqueue/TtsQueueReducer.kt`
- Modify: `app/src/test/java/me/ash/reader/infrastructure/android/ttsqueue/TtsQueueControllerTest.kt`

- [ ] Add bookmark storage keyed by article id and keep it in queue snapshots.
- [ ] Update play, pause, resume, seek, remove, and clear flows to keep bookmarks correct.
- [ ] Verify with targeted queue controller tests only.

### Task 2: Weighted progress mapping

**Files:**
- Modify: `app/src/main/java/me/ash/reader/infrastructure/android/TextToSpeech.kt`
- Create: `app/src/main/java/me/ash/reader/ui/page/home/reading/queue/TtsPlaybackProgressBar.kt`
- Create: `app/src/test/java/me/ash/reader/ui/page/home/reading/queue/TtsPlaybackProgressBarStateTest.kt`
- Create: `app/src/test/java/me/ash/reader/infrastructure/android/TextToSpeechChunkingTest.kt`

- [ ] Expose enough per-segment information to compute length-weighted display progress and seek mapping.
- [ ] Split long single-paragraph content into lightweight extra speakable chunks so low-segment articles still support useful dragging.
- [ ] Implement the custom progress bar helper and weighted conversion functions.
- [ ] Verify with pure state tests for fraction and segment mapping.

### Task 3: Fixed player card and stable queue list

**Files:**
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/reading/queue/TtsMiniPlayer.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/reading/queue/TtsQueueSheet.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/adaptive/ArticleListReadingPage.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/adaptive/ArticleListReaderViewModel.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/flow/FlowPage.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/flow/ScrollToPositionFab.kt`

- [ ] Turn the top controls into a fixed player card with large previous / play-pause / next actions.
- [ ] Keep list order stable when tapping a list item to play it.
- [ ] Make queue item title area play the item and keep move/delete controls on the right.
- [ ] Make the player-card title open the current article reading page.
- [ ] Make previous and next wrap around at queue boundaries.
- [ ] Make system back close the queue sheet first when the sheet is open.
- [ ] Add a bottom-right queue entry on the article flow page that opens the same queue sheet.
- [ ] Verify with lightweight queue UI state tests plus one Kotlin compile.
