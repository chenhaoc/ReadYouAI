# TTS Player Card Design

## Goal

Replace the scrolling queue-item progress UI with a fixed player card at the top of the queue sheet, keep queue order stable while switching playback, persist playback progress per article, and expose queue controls through a global floating entry instead of a page-bound mini player.

## Confirmed Interaction

- The queue sheet shows a fixed, taller player card above the scrollable list.
- The player card contains:
  - current article title
  - feed name
  - previous / play-pause / next controls
  - one full-width draggable progress bar
- Tapping the player card title opens the current article reading page.
- Queue sheet presentation is root-scoped, so the same playlist drawer is shared by feeds, flow, and reading contexts.
- The queue list remains scrollable below the card.
- Tapping the title/info area of a queue item starts playback for that item.
- Queue item order does not change when playback changes.
- Queue items keep explicit move-up, move-down, and delete controls.
- Previous and next wrap at queue boundaries instead of stopping on the first or last item.
- While the queue sheet is open, system back should close the queue sheet before leaving the page.
- Feeds and flow pages expose the queue from a fourth bottom-bar action, and style previews should show the same extra action.
- The reading mini player is removed.
- A global floating queue button is available when the queue is non-empty and the user enables it in settings.
- The floating button:
  - supports left/right edge docking
  - opens the queue sheet on tap
  - toggles play/pause on long press
  - snaps to the nearest edge after drag release

## Playback Progress Model

- Progress is stored per article, not just for the currently active queue item.
- Each bookmark stores:
  - article id
  - current segment index
  - total segment count
  - segment character weights for length-weighted progress display and seek mapping
- Progress should survive:
  - pause/resume
  - switching from article A to B and back to A
  - temporary audio interruption and resume
  - app restart, as long as the article remains in the queue
- Removing an article from the queue deletes that article's bookmark.
- Clearing the queue deletes all bookmarks.
- Long single-paragraph content may be split into smaller speakable chunks so low-segment articles still provide usable seek anchors.

## Progress UX

- Do not use the default Material slider visual.
- Use a custom full-width track with:
  - thin inactive rail
  - highlighted played rail
  - visible draggable thumb
- Progress is approximate but should be length-weighted by text amount, not evenly split by segment count.
- Seek remains segment-based; drag position maps to the nearest weighted segment.
- When a source article has very few natural segments, the chunking layer should introduce lightweight extra segments rather than faking purely visual seek positions.

## Non-Goals

- No sentence-level or character-level exact resume.
- No separate draggable progress UI on the reading page.
- No automatic queue reordering on play.
- No freeform floating-button position persistence beyond left/right docking.
