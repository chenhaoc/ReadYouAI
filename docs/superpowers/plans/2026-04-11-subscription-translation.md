# Subscription Translation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add subscription-scoped article translation with manual and automatic triggers, paragraph-level bilingual rendering, and cached Simplified Chinese translations.

**Architecture:** Extend `Feed` and `Article` persistence for translation state, add a shared content-block extraction pipeline, reuse the existing AI provider settings with a dedicated translation prompt, and render bilingual output through both native and WebView reading paths from the same block model.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Gson, Jsoup, Retrofit/OpenAI-compatible chat completions, JUnit4

---

### Task 1: Cover translation state logic with tests

**Files:**
- Modify: `app/src/test/java/me/ash/reader/ui/page/adaptive/ReadingUiStateTest.kt`
- Create: `app/src/test/java/me/ash/reader/ui/page/home/reading/ArticleTranslationSupportTest.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/adaptive/ArticleListReaderViewModel.kt`
- Create: `app/src/main/java/me/ash/reader/ui/page/home/reading/ArticleTranslationSupport.kt`

- [ ] Add failing tests for translation state visibility, auto-translate gating, and feed toggle normalization.
- [ ] Run `./gradlew :app:testGithubDebugUnitTest --tests 'me.ash.reader.ui.page.adaptive.ReadingUiStateTest' --tests 'me.ash.reader.ui.page.home.reading.ArticleTranslationSupportTest'` and verify the new assertions fail for missing behavior.
- [ ] Implement the minimal derived state and helper functions to satisfy those tests.
- [ ] Re-run the same targeted tests and verify they pass.

### Task 2: Build and test the shared content-block extraction layer

**Files:**
- Create: `app/src/main/java/me/ash/reader/ui/page/home/reading/ArticleContentBlock.kt`
- Create: `app/src/main/java/me/ash/reader/ui/page/home/reading/ArticleContentBlockParser.kt`
- Create: `app/src/test/java/me/ash/reader/ui/page/home/reading/ArticleContentBlockParserTest.kt`

- [ ] Add failing tests for parsing paragraphs, headings, blockquotes, unordered/ordered list items, code blocks, images, and source hash stability.
- [ ] Run `./gradlew :app:testGithubDebugUnitTest --tests 'me.ash.reader.ui.page.home.reading.ArticleContentBlockParserTest'` and verify the parser tests fail first.
- [ ] Implement a Jsoup-backed parser that produces stable ordered blocks and translation source payloads.
- [ ] Re-run the parser test command and verify it passes.

### Task 3: Add persisted translation state and prompt preference

**Files:**
- Modify: `app/src/main/java/me/ash/reader/domain/model/feed/Feed.kt`
- Modify: `app/src/main/java/me/ash/reader/domain/model/article/Article.kt`
- Modify: `app/src/main/java/me/ash/reader/domain/repository/FeedDao.kt`
- Modify: `app/src/main/java/me/ash/reader/domain/repository/ArticleDao.kt`
- Modify: `app/src/main/java/me/ash/reader/infrastructure/db/AndroidDatabase.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/ext/DataStoreExt.kt`
- Modify: `app/src/main/java/me/ash/reader/infrastructure/preference/Settings.kt`
- Modify: `app/src/main/java/me/ash/reader/infrastructure/preference/SettingsProvider.kt`
- Create: `app/src/main/java/me/ash/reader/infrastructure/preference/AiTranslationPromptPreference.kt`

- [ ] Add a failing test or compile-time coverage for the new prompt preference mapping and article/feed fields.
- [ ] Implement the Room schema updates, migration, DAO updates, and new translation prompt preference plumbing.
- [ ] Run targeted unit tests plus compile verification for these files.

### Task 4: Add translation request and response handling

**Files:**
- Create: `app/src/main/java/me/ash/reader/domain/repository/AiTranslationRepository.kt`
- Modify: `app/src/main/java/me/ash/reader/infrastructure/di/OpenAiModule.kt`
- Create: `app/src/test/java/me/ash/reader/domain/repository/ArticleTranslationPayloadTest.kt`

- [ ] Add failing tests for translation payload parsing and strict ID coverage validation.
- [ ] Run `./gradlew :app:testGithubDebugUnitTest --tests 'me.ash.reader.domain.repository.ArticleTranslationPayloadTest'` and confirm the tests fail before implementation.
- [ ] Implement the repository request builder, JSON parsing, and all-or-nothing ID validation.
- [ ] Re-run the targeted payload test and verify it passes.

### Task 5: Wire feed settings and AI settings UI

**Files:**
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/feeds/FeedOptionView.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/feeds/drawer/feed/FeedOptionViewModel.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/feeds/drawer/feed/FeedOptionDrawer.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/settings/ai/AiSettingsPage.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

- [ ] Add any missing unit coverage for toggle dependency helpers used by the view model.
- [ ] Implement feed drawer toggles and AI settings prompt editing UI.
- [ ] Run targeted tests for helper logic and a compile check for the updated UI files.

### Task 6: Add translation flow to the reading view model

**Files:**
- Modify: `app/src/main/java/me/ash/reader/ui/page/adaptive/ArticleListReaderViewModel.kt`
- Modify: `app/src/test/java/me/ash/reader/ui/page/adaptive/ReadingUiStateTest.kt`

- [ ] Extend the failing tests to cover translation loading, visibility, and auto-translate gating with cached hashes.
- [ ] Run the focused `ReadingUiStateTest` command again and verify failure.
- [ ] Implement manual translation, automatic translation, cache reuse, refresh behavior, and translation error handling in the view model.
- [ ] Re-run the focused state tests and any new translation helper tests.

### Task 7: Render bilingual output in native and WebView readers

**Files:**
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/reading/Content.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/reading/ReadingPage.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/page/home/reading/TopBar.kt`
- Modify: `app/src/main/java/me/ash/reader/ui/component/webview/WebViewHtml.kt`
- Create: `app/src/main/java/me/ash/reader/ui/page/home/reading/ArticleTranslationRenderer.kt`

- [ ] Add parser/renderer helper tests if needed for generated bilingual HTML ordering.
- [ ] Implement the top-bar translate action and loading spinner.
- [ ] Render original and translated blocks in native reading mode.
- [ ] Generate local bilingual HTML for WebView mode from the same block list and translation payload.
- [ ] Run the targeted parser/renderer tests plus a compile check to verify the reading UI builds.

### Task 8: Verify the integrated feature end to end

**Files:**
- Modify: `docs/superpowers/plans/2026-04-11-subscription-translation.md`

- [ ] Run `./gradlew :app:testGithubDebugUnitTest --tests 'me.ash.reader.ui.page.adaptive.ReadingUiStateTest' --tests 'me.ash.reader.ui.page.home.reading.ArticleTranslationSupportTest' --tests 'me.ash.reader.ui.page.home.reading.ArticleContentBlockParserTest' --tests 'me.ash.reader.domain.repository.ArticleTranslationPayloadTest'`.
- [ ] Run `./gradlew :app:compileGithubDebugKotlin`.
- [ ] Run `./gradlew :app:testGithubDebugUnitTest` and record the known baseline failure separately from any new failures.
- [ ] Update this plan checklist status as work completes.
