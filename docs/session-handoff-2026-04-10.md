# Session Handoff 2026-04-10

## Current Branch

- Branch: `codex/pr-1210-ai-summary`

## Recent Commits

- `8688b317` `build(ai): default to parallel install release builds`
- `6b8c6c4d` `feat(ai): add inline auto summary workflow`
- `cccd3ac1` `build(apk): add single-core release build script`
- `af52cc9e` `feat(ai): add article summarization support`

## Current Build Defaults

- Default build script: [scripts/build-github-debug.sh](/Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/build-github-debug.sh)
- Default task: `assembleGithubAiRelease`
- Package id for parallel install flavor: `me.ash.reader.ai`
- Display name for parallel install flavor: `Read You AI`
- Build is constrained to single-core Gradle/JVM settings

## Verified APK Outputs

- Parallel install release:
  [ReadYou-0.16.1-8688b317.apk](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/build/outputs/apk/githubAi/release/ReadYou-0.16.1-8688b317.apk)
- Main github release from earlier checkpoint:
  [ReadYou-0.16.1-cccd3ac1.apk](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/build/outputs/apk/github/release/ReadYou-0.16.1-cccd3ac1.apk)

## What Is Implemented

### AI Summary Base Feature

- AI summary settings page exists
- Manual `AI Summary` trigger exists in reading top bar
- OpenAI-compatible API request flow exists

### Inline Summary UX

- Summary is no longer shown as a blocking full-screen dialog
- Summary is rendered inline above article content
- Summary is persisted on the article record
- Reopening an article with an existing summary shows it expanded by default

### Auto Summary Workflow

- New `Auto AI Summary` setting exists in AI settings
- When enabled, opening an article without a summary auto-triggers summary generation
- Auto generation is silent: it does not jump scroll position or force the page to the top
- After generation completes, a lightweight top prompt is shown with text `Summary`
- The prompt contains `View`, which scrolls to the summary and expands it
- Manual summary generation follows the same non-jumping behavior

### Parallel Install Flavor

- New flavor: `githubAi`
- New package id: `me.ash.reader.ai`
- Can be installed alongside the original `me.ash.reader` app

## Key Files Changed

- Flavor/build:
  - [app/build.gradle.kts](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/build.gradle.kts)
  - [scripts/build-github-debug.sh](/Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/build-github-debug.sh)
  - [docs/build-script.md](/Users/hao.chen/工作文档/Work/readyou/ReadYou/docs/build-script.md)

- Persistence:
  - [Article.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/domain/model/article/Article.kt)
  - [ArticleDao.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/domain/repository/ArticleDao.kt)
  - [AndroidDatabase.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/infrastructure/db/AndroidDatabase.kt)
  - [8.json](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/schemas/me.ash.reader.infrastructure.db.AndroidDatabase/8.json)

- AI preferences:
  - [AiAutoSummaryPreference.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/infrastructure/preference/AiAutoSummaryPreference.kt)
  - [Preference.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/infrastructure/preference/Preference.kt)
  - [Settings.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/infrastructure/preference/Settings.kt)
  - [SettingsProvider.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/infrastructure/preference/SettingsProvider.kt)
  - [DataStoreExt.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/ui/ext/DataStoreExt.kt)
  - [AiSettingsPage.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/ui/page/settings/ai/AiSettingsPage.kt)

- Reading flow / UI:
  - [ArticleListReaderViewModel.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/ui/page/adaptive/ArticleListReaderViewModel.kt)
  - [ReadingPage.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/ui/page/home/reading/ReadingPage.kt)
  - [Content.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/ui/page/home/reading/Content.kt)
  - [AiSummaryOverlay.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/java/me/ash/reader/ui/page/home/reading/AiSummaryOverlay.kt)
  - [strings.xml](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/main/res/values/strings.xml)

- Tests:
  - [ReadingUiStateTest.kt](/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/src/test/java/me/ash/reader/ui/page/adaptive/ReadingUiStateTest.kt)

## Uncommitted Files

Only process docs remain uncommitted:

- [2026-04-10-parallel-install-flavor.md](/Users/hao.chen/工作文档/Work/readyou/ReadYou/docs/superpowers/plans/2026-04-10-parallel-install-flavor.md)
- [2026-04-10-ai-summary-inline-reading-design.md](/Users/hao.chen/工作文档/Work/readyou/ReadYou/docs/superpowers/specs/2026-04-10-ai-summary-inline-reading-design.md)

These are not part of the product change commits yet.

## Validation Completed

- `testGithubDebugUnitTest --tests 'me.ash.reader.ui.page.adaptive.ReadingUiStateTest'`
  - Passed
- `./scripts/build-github-debug.sh`
  - Builds `githubAiRelease`
  - Produced a successful release APK

## Known Gaps

- No emulator install/run verification has been completed yet
- No real device interaction verification has been completed yet
- The `AboutLibraries` task prints a variant warning for `githubAiRelease`, but the build still succeeds

## Recommended Next Steps

1. If desired, install the parallel APK on a device or emulator and verify:
   - it installs alongside the original app
   - app name shows as `Read You AI`
   - auto summary setting works
   - `Summary` prompt appears without scroll jump
   - `View` jumps to inline summary and expands it
2. Decide whether to commit or discard the `docs/superpowers/` process documents
3. If continuing feature work, start from branch `codex/pr-1210-ai-summary`
