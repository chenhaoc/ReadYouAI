# AI Summary Inline Reading Design

## Goal

Replace the current full-screen AI summary dialog with an inline summary card rendered at the top of the reading content, so users can keep reading the article while the summary is loading or after it has been generated.

The summary must persist per article. If a summary has already been generated for an article, reopening that same article should show the summary card automatically without requiring the user to generate it again.

## Current State

- The reading UI triggers AI summary generation from `ReadingPage`.
- Generated content is held only in page-local Compose state.
- The UI is rendered with `AiSummaryOverlay`, which uses a full-screen `Dialog`.
- Closing the page loses the generated summary because it is not persisted.

## Requirements

### Functional

1. Tapping `AI Summary` should no longer open a blocking dialog.
2. The reading page should render an inline AI summary card above the article body.
3. The card should support these states:
   - hidden when no summary exists and generation has never started
   - loading when summary generation is in progress
   - success when summary text exists
   - error when generation fails
4. Generated summary content should be persisted per article.
5. Reopening the same article should restore and show the persisted summary automatically.
6. Tapping `AI Summary` for an article that already has a summary should refresh the summary in place.
7. The user must still be able to read and scroll the original article content while summary generation is happening.

### UX

1. The summary card should appear below the article metadata area and above the main article content.
2. The card should default to expanded when a summary exists or generation is running.
3. The card should offer collapse/expand behavior so it can be minimized without removing it.
4. Loading and error states should live inside the same card container, not in a modal surface.
5. The card should visually read as part of the article, not as a floating overlay.

## Recommended Approach

Persist the summary on the `article` record and render a dedicated inline composable inside the reading content tree.

### Why This Approach

- Persistence is naturally keyed by article id.
- Reopening the same article can reuse the same data path already used for article-backed reading state.
- The UI change stays local to the reading page rather than introducing a separate side cache or overlay lifecycle.
- The implementation matches the user-facing mental model: the summary becomes part of the article view.

## Alternatives Considered

### 1. Separate `article_ai_summary` table

Pros:
- Clean separation from core article schema
- Easier future expansion for metadata like model name, generated time, or prompt hash

Cons:
- More DAO and migration surface than the current requirement needs
- Adds an extra join or lookup path for a simple per-article text field

### 2. File-based cache like readability cache

Pros:
- No Room schema change
- Easy to write and overwrite

Cons:
- Harder to query summary presence from UI state
- Harder to manage lifecycle and cleanup consistently
- Less natural fit for “show automatically when reopening article”

## Architecture

### Data Layer

Add persisted AI summary fields to `Article` and expose DAO methods for:

- reading summary content by article id
- writing or replacing summary content by article id
- clearing summary content if needed in future

At minimum, one nullable text field is required:

- `aiSummary: String?`

Optionally, one timestamp can be added if the implementation benefits from refresh/debug visibility:

- `aiSummaryUpdatedAt: Date?`

For the current request, `aiSummary` alone is sufficient.

### ViewModel Layer

Move AI summary state ownership into `ArticleListReaderViewModel` so the reading page can observe:

- persisted summary for the current article
- loading state for the current article
- error state for the current article
- collapsed/expanded UI state

The ViewModel should:

1. Load persisted summary when the current article changes.
2. Expose a single state object for inline summary rendering.
3. On generate or regenerate:
   - set loading state
   - call `AiSummaryRepository`
   - persist success into the article record
   - update UI state from persisted value
   - surface error in the card if generation fails

The collapse/expand preference can remain ephemeral page UI state; it is out of scope for persistence in this change.

### UI Layer

Replace `AiSummaryOverlay` usage with a new inline composable rendered inside the reading content stack.

The new composable should:

- render above the article body
- show a title row with icon/title and a collapse affordance
- show loading, success, and error states in the same container
- avoid intercepting the whole screen

`ReadingPage` should stop owning summary text/loading/error in local mutable state once the ViewModel state exists.

## Rendering Placement

The summary card should be inserted at the top of the article reading content, after title/feed/date metadata and before the main article text or HTML body.

This placement preserves:

- immediate visibility
- uninterrupted article scrolling
- a stable layout that feels like article context rather than a popup

## State Model

Suggested inline state shape:

- `articleId: String?`
- `summary: String?`
- `isLoading: Boolean`
- `error: String?`
- `isExpanded: Boolean`
- `hasAttemptedGeneration: Boolean`

Behavior:

- `summary != null` means the card should show automatically
- `isLoading == true` means the card should show even if `summary == null`
- `error != null` means the card should remain visible after a failed attempt
- no summary and no attempt means the card stays hidden

## Error Handling

- Missing API key or base URL should render an inline error message in the card.
- Network or provider errors should stay inside the card and not block the rest of the article.
- Refresh attempts should clear old transient error state before retrying.
- Existing persisted summary should remain visible if a refresh attempt fails.

## Data Migration

Because this change persists summary data on articles, Room schema versioning must be updated.

Migration requirements:

- add nullable `aiSummary` column to `article`
- default existing rows to `NULL`
- preserve compatibility with existing user databases

This is a low-risk additive migration.

## Testing Strategy

### Data

- verify migration adds the new nullable column correctly
- verify article summary can be written and read back by id

### ViewModel

- opening an article with existing summary exposes visible summary state
- generating a summary persists success and clears loading/error
- generation failure sets error without losing an existing persisted summary

### UI

- reading page shows inline card instead of modal dialog
- loading state does not block scrolling or reading content
- success state renders summary above article body
- collapse/expand toggles only the card body, not the whole card shell

## Non-Goals

- syncing summaries to remote RSS providers
- caching multiple summaries per article
- persisting collapsed/expanded state across app restarts
- redesigning the AI settings flow

## Implementation Notes

- `AiSummaryOverlay` should be removed or left unused after the inline component is introduced.
- If the existing reading content tree is split between description/text modes, the summary card should be injected through the shared top-level article content path so behavior is consistent regardless of content type.
- Keep the first iteration narrow: one persisted summary text per article, inline card UI, and in-place refresh.
