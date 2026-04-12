# Subscription Translation Design

## Summary

Add subscription-scoped translation controls to ReadYou so that individual subscriptions can opt in to article translation. For enabled subscriptions, the reading top bar will expose a manual `Translate` action with a loading spinner, subscriptions can also opt into automatic translation on article open, and feed-scoped article lists can automatically pre-translate visible articles.

The first version will translate article text blocks into Simplified Chinese and render them in bilingual form: each original text block is followed immediately by its translated counterpart. This design intentionally avoids whole-document replacement and instead introduces a structured content-block pipeline so both reading renderers can share the same translation result.

## Goals

- Let each subscription independently enable or disable translation.
- Let each subscription independently enable or disable automatic translation.
- Show a reading-page translation button for articles from translation-enabled subscriptions.
- Show a spinner while translation is in progress.
- Render bilingual reading content as `original block -> translated block`.
- Cache translation results per article to avoid repeated requests.
- Reuse article translation cache to show translated title and summary in feed-scoped article lists.
- Reuse the existing AI provider configuration (`base URL`, `API key`, `model`) instead of creating a second provider stack.

## Non-Goals

- Multi-language translation targets.
- Original/translation view switching modes.
- Partial paragraph selection translation.
- Streaming translation into the page while the request is still in progress.
- Writing translated text back into article HTML.
- Translating non-text media blocks such as images, videos, or embedded content.
- Fine-grained table-cell translation in the first version.

## User Experience

### Subscription Settings

Each feed gains two new toggles in:

- the existing feed option drawer
- the new subscription configuration dialog shown before confirming a newly discovered feed

- `Enable translation`
- `Auto translate`

Interaction rules:

- Enabling `Auto translate` implicitly enables `Enable translation`.
- Disabling `Enable translation` also disables `Auto translate`.
- `Auto translate` is only meaningful when translation is enabled.

### Reading Page

For articles whose feed has translation enabled:

- The top bar shows a `Translate` icon action.
- If no translation is running, tapping the action starts translation.
- If translation is running, the action area shows a spinner and is disabled.
- If translation already exists, tapping the action forces a refresh.

For translated articles:

- Text content is rendered in bilingual form.
- Each supported source block is followed by a translated Chinese block.
- Unsupported non-text blocks are shown only once in their original form.
- Translation appears progressively instead of waiting for the full article to finish.

### Automatic Translation

When an article opens, translation starts automatically only if all of the following are true:

- The article's feed has translation enabled.
- The article's feed has auto-translate enabled.
- The article has no valid cached translation for the current source content.
- There is no in-flight translation request for the same article.

Automatic and manual translation failures surface an error to the user so timeouts and provider issues are visible instead of silently disappearing.

### Feed Article List

When the user enters a single feed whose translation is enabled:

- The article list automatically detects currently visible articles.
- The list translation queue prioritizes visible articles first.
- The queue then prefetches the next four articles after the visible window.
- Up to five articles are translated concurrently in the background.
- List items switch to translated title and translated summary as soon as cached blocks become available.
- If no translation cache exists yet, the list item falls back to the original title and short description.

This behavior intentionally applies only to feed-scoped lists, not group views or the global article list, so automatic translation stays bounded and predictable.

## Design Options Considered

### Option A: Structured Content Blocks and Structured Translation Output

Parse article content into a stable list of renderable content blocks, translate only eligible text blocks, store the translated block payload, and render bilingual output from the combined structure.

Pros:

- Stable for both native and WebView reading renderers.
- Translation results can be matched back to exact blocks by ID.
- Future-friendly for collapse, hide, or export behaviors.

Cons:

- Requires new parsing and rendering layers.
- Larger initial implementation scope.

### Option B: Ask the Model to Return Bilingual HTML

Send the entire article HTML to the model and ask it to insert translated text after each paragraph.

Pros:

- Fastest path for WebView-only rendering.

Cons:

- Fragile HTML output.
- Easy to break tags, lists, links, and embedded content.
- Native renderer cannot reliably reuse the result.

### Option C: Support Only One Reading Renderer

Implement bilingual translation only for one renderer in v1 and degrade or disable the feature in the other renderer.

Pros:

- Smaller first implementation.

Cons:

- Inconsistent product behavior.
- Harder to explain and maintain.

## Chosen Approach

Use **Option A**.

The core of the feature is not just API invocation; it is reliable bilingual presentation. A structured content-block pipeline is the most robust way to preserve reading layout across both renderers while supporting the `original block -> translated block` requirement.

## Architecture

### Feed-Level State

Extend `Feed` with:

- `isTranslationEnabled: Boolean = false`
- `isAutoTranslate: Boolean = false`

Persist both fields in Room and expose toggle actions through the existing `FeedOptionViewModel`.

### Article-Level State

Extend `Article` with:

- `translationBlocksZh: String? = null`
- `translationSourceHash: String? = null`

`translationBlocksZh` stores serialized structured translation output for Simplified Chinese. `translationSourceHash` identifies the exact source text-block payload used to generate that translation. The stored payload may be partial while progressive translation is still running, but it must always remain valid JSON and preserve already translated block IDs.

No separate translation table is needed in v1 because:

- Only one target language is supported.
- Translation is always article-scoped.
- A single cached translation payload is sufficient for the current scope.

### Content Block Pipeline

Introduce a normalized content model for reading output, for example:

- `Heading`
- `Paragraph`
- `ListItem`
- `Quote`
- `CodeBlock`
- `Image`
- `Video`
- `Divider`

Only text-bearing blocks participate in translation:

- `Heading`
- `Paragraph`
- `ListItem`
- `Quote`

Each text block receives a deterministic `blockId` based on traversal order and type, such as `heading_3` or `paragraph_12`.

### Translation Payload Format

Before translation, extract a list of source blocks:

```json
[
  { "id": "heading_1", "type": "heading", "text": "..." },
  { "id": "paragraph_1", "type": "paragraph", "text": "..." }
]
```

The AI prompt instructs the model to return only structured JSON with the same IDs:

```json
[
  { "id": "heading_1", "translatedText": "..." },
  { "id": "paragraph_1", "translatedText": "..." }
]
```

The client then maps the returned list back to source blocks by ID.

### List Translation Preview

The article list does not store a second list-only translation payload.

Instead, it derives a lightweight preview directly from `translationBlocksZh`:

- list title: the dedicated translated `list_title` block
- list summary: the translated first paragraph block, or the first non-heading translated body block when no paragraph exists

If translated blocks are absent or invalid, the list falls back to `Article.title` and `Article.shortDescription`.

The reading-page metadata header reuses the same `list_title` block. When present, the header shows the original title first and the translated Chinese title directly below it, keeping the `original -> translation` reading order consistent with the bilingual article body.

### Hashing and Cache Validity

Generate `translationSourceHash` from the exact ordered translation-source payload, not from raw HTML.

This ensures the cache invalidates when:

- block ordering changes,
- source text changes,
- parsing produces different translatable text.

The cache remains valid when:

- unrelated HTML structure changes,
- non-translatable blocks change,
- visual styling changes only.

## Reading Renderer Strategy

### Native Renderer

Replace the current direct `Reader(content = ...)` rendering path with a block-based renderer that emits:

- original text block
- translated block if present
- non-text block once in original form

This renderer should preserve current spacing and typography as much as possible, while introducing a distinct style for translated text so users can visually separate it from the original.

### WebView Renderer

Do not ask the model to generate bilingual HTML.

Instead, build a bilingual HTML document from the same normalized content-block list plus the cached translation map. The WebView receives final HTML assembled locally, using the existing style shell.

This keeps parity with the native renderer and reduces malformed-model-output risk.

## Translation Request Flow

### Manual Translation

1. User taps the top-bar `Translate` button.
2. The view model checks whether a translation request is already in flight.
3. The article content is parsed into normalized blocks.
4. Existing translated block IDs are loaded from cache when the source hash still matches.
5. The app chooses a prioritized batch of untranslated blocks, starting near the current reading position.
6. The repository translates only that batch using the shared AI settings and translation prompt.
7. On success, the app merges the returned blocks into the cached JSON payload and updates the reading UI immediately.
8. The same job continues requesting additional prioritized batches until all eligible blocks are translated or an error occurs.

### Automatic Translation

1. Article opens.
2. The view model resolves the current article and its feed.
3. If translation is enabled and auto-translate is enabled, the article content is parsed.
4. The app starts translating the first prioritized batch near the current reading position instead of waiting for the full article.
5. Additional batches continue in the background while the article is being read.
6. If translation fails, the app keeps already translated blocks and shows an error prompt.

### Feed List Translation

1. User opens a feed whose translation is enabled.
2. The feed list watches visible article items and waits briefly for scrolling to settle.
3. The app collects visible article IDs, then appends the next four article IDs after the visible window.
4. The resulting queue is deduplicated and compared to the previous queue to avoid redundant work.
5. The view model translates up to five queued articles concurrently.
6. Each queued article sends only two fields for translation: the article title and the first paragraph body block, with a non-heading body block fallback only when no paragraph exists.
7. Each queued article writes the result back into `translationBlocksZh` using the shared article translation cache.
8. Once cache is updated, paging invalidation refreshes the corresponding list row and the translated preview appears.

For full-content feeds, the list translation path only uses existing full-content cache. It does not fetch missing full content during list scrolling, which avoids combining full-content fetch latency with translation latency in the scrolling path.

### Refresh Translation

If translated content already exists, tapping the `Translate` button restarts progressive translation using the current source hash and refreshes the cached payload incrementally.

### Chunking Strategy

Long articles should not be translated as one monolithic request. The batching strategy is intentionally conservative:

- prioritize untranslated blocks closest to the current reading position
- estimate output size per block
- cap each request to roughly `450-500` estimated output tokens
- also keep a JSON payload size ceiling as a secondary guardrail
- never split a single source block across requests

This reduces provider timeout risk for long articles while still preserving paragraph-level bilingual display.

## Error Handling

### Missing AI Settings

If `base URL` or `API key` is empty:

- manual translation shows a user-facing error,
- automatic translation does nothing.

### Malformed Translation Output

If the model returns invalid or incomplete structured data:

- treat the request as failed,
- keep the previous translation cache if one exists,
- surface an error to the user.

### Partial ID Coverage

If some IDs are missing from the returned payload:

- discard the entire response for v1,
- keep the previous translation cache if one exists,
- otherwise leave the article untranslated,
- surface an error to the user.

This keeps the first version predictable and avoids half-translated article states.

## Prompting Strategy

Reuse the existing AI settings page for provider configuration and model selection.

Add a new translation prompt preference with a default prompt that explicitly requires:

- Simplified Chinese output,
- one translated result per input block,
- preserved block order,
- no summarization,
- no omission,
- valid JSON-only output.

The summarization prompt remains separate from the translation prompt.

## Database and Migration Plan

### Feed Table Changes

Add:

- `isTranslationEnabled INTEGER NOT NULL DEFAULT 0`
- `isAutoTranslate INTEGER NOT NULL DEFAULT 0`

### Article Table Changes

Add:

- `translationBlocksZh TEXT DEFAULT NULL`
- `translationSourceHash TEXT DEFAULT NULL`

### Room Versioning

Increment the Room database version and add a dedicated migration for these four new columns.

## UI State Additions

Extend reading UI state with translation-specific fields, parallel to the existing summary state:

- `translatedBlockMap` or parsed translation payload
- `isTranslationLoading`
- `translationError`
- `hasAutoTranslationAttempted`
- `translationFocusIndex`
- an in-flight translation job handle for progressive batching

Derived state should answer:

- whether translation is available for this article,
- whether auto-translation should run,
- whether the top bar should show a spinner,
- whether bilingual rendering should be active.

## Test Plan

### Unit Tests

- Feed toggle dependency logic.
- Content-block extraction for representative HTML.
- Stable block ID generation.
- Stable source hash generation.
- Translation JSON parsing and ID mapping.
- Progressive translation batch prioritization.
- Long-article request chunking under the estimated-token limit.
- Auto-translate gating logic.
- Cache reuse when source hash matches.
- Cache invalidation when source hash differs.

### UI/Behavior Tests

- Translation button visibility depends on feed settings.
- Subscription configuration dialog persists translation toggles for newly added feeds.
- Spinner appears during translation and disappears afterward.
- Translation errors surface to the user.
- Bilingual content renders in correct block order.
- Long articles can show partial translated content before the entire article finishes.

### Regression Focus

- Existing summary flow remains unaffected.
- Existing reading renderers still render untranslated articles correctly.
- Image, code block, and other non-text content remain visible and ordered correctly.

## Risks and Mitigations

### Risk: Parser Drift Between Renderers

If native and WebView paths use different parsing logic, bilingual output will diverge.

Mitigation:

- introduce one shared normalized content-block extraction layer used by both renderers.

### Risk: Model Output Instability

Structured JSON prompts can still fail.

Mitigation:

- require strict JSON output,
- validate IDs before persistence,
- fail closed instead of storing partial or malformed translations.

### Risk: Long-Article Provider Timeouts

Even valid structured JSON responses can fail if a single request produces too many output tokens.

Mitigation:

- translate in prioritized batches instead of one full-article request
- cap each batch to a small estimated output budget
- persist partial progress after every successful batch

## Baseline Verification Note

The current `githubDebug` unit test baseline is already not clean before translation work starts:

- `BackupRestorePayloadTest > roundTripsConcreteBackupDtos` fails on the current base branch.

This is treated as a pre-existing issue and should be tracked separately from the translation feature work.

## Implementation Outline

1. Add database fields and migration for feed/article translation state.
2. Add feed drawer toggles and dependency behavior.
3. Add translation prompt preference and settings UI.
4. Introduce shared content-block extraction and hashing.
5. Add translation repository API and structured response parsing.
6. Extend reading view model with manual and automatic translation flows.
7. Add top-bar translate action and loading spinner.
8. Render bilingual block output in native and WebView reading paths.
9. Add targeted unit tests for toggles, parsing, hashing, and translation gating.
