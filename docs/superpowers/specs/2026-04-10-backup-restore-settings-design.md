# Backup Restore Settings Design

## Goal

Add a dedicated settings page for exporting and importing app configuration so the user can quickly restore preferences in a fresh install or another build variant.

## Scope

This feature only handles DataStore-backed application preferences.

It does not export or import:

- accounts
- feeds and groups
- article database content
- AI summaries stored on articles
- cache files

## Current State

The project already contains reusable configuration serialization logic:

- `fromDataStoreToJSONString()`
- `fromJSONStringToDataStore()`

It also already contains a ViewModel with import/export helpers in troubleshooting code.

The missing piece is a user-facing settings flow for normal use.

## Requirements

1. Add a new entry on the main settings page named `Backup & Restore`.
2. Add a dedicated page with two primary actions:
   - export configuration
   - import configuration
3. Export should produce a JSON file with a timestamped filename.
4. Import should show a clear confirmation before overwriting current preferences.
5. The feature should reuse existing DataStore serialization logic instead of inventing a new format.
6. The first version should not attempt partial merge or conflict resolution.

## Recommended Approach

Create a new `BackupRestorePage` and reuse the existing troubleshooting import/export methods, either through the existing ViewModel or by extracting a small shared helper if needed during implementation.

## UX

### Entry

- Add a new settings entry on the main settings page
- Label: `Backup & Restore`
- Description: `Export or import app preferences`

### Page

Show two simple actions:

1. `Export configuration`
2. `Import configuration`

### Export

- Opens platform document create flow
- Default filename format:
  `read-you-settings-YYYY-MM-DD-HH-mm.json`
- Writes exported JSON bytes to the selected destination
- Shows success or failure feedback

### Import

- Opens platform document picker
- Reads selected JSON file
- Shows confirmation dialog before applying
- On confirm, imports and applies preferences
- Shows success or failure feedback

## Architecture

### UI Layer

Add:

- settings entry on main settings page
- route for new backup/restore page
- page UI with export and import actions
- import confirmation dialog

### ViewModel Layer

Reuse existing logic from troubleshooting flow where practical.

If direct reuse is awkward, extract a narrow shared function instead of duplicating serialization and import logic.

### Data Layer

No new persistence format is needed.

Reuse:

- `fromDataStoreToJSONString()`
- `fromJSONStringToDataStore()`

## Non-Goals

- exporting database content
- selective import
- cloud backup
- encrypted export
- compatibility guarantees across arbitrary future schema changes

## Risks

1. Import can overwrite preferences unexpectedly if confirmation is weak.
2. Importing malformed JSON must fail cleanly.
3. The page should stay clearly scoped to preferences only so users do not mistake it for full app backup.

## Testing

1. Verify export action produces non-empty JSON.
2. Verify import action applies preferences from a valid exported file.
3. Verify invalid JSON import fails without crashing.
4. Verify main settings page navigation reaches the new page.
