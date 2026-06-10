# Calendar Journal — Product Roadmap

A polished personal journaling app: a calendar where clicking a day opens an editor
to write and save an entry. Local-first, no cloud — your journal stays on your machine.

**Direction**
- **Audience:** a genuinely nice daily-driver journaling app for personal use.
- **Platform:** Java desktop, shipped as native installers (Linux/Windows/macOS) via `jpackage`.
- **UI stack:** JavaFX (CSS theming, `WebView` Markdown preview, built-in charts).
- **No** cloud, accounts, sync, mobile/web, or AI — by design.

---

## Today — v0.1 (shipped)

| Area | Status |
|------|--------|
| Month calendar with prev/next navigation, today highlighted | ✅ |
| One plain-text entry per day; Save / Delete | ✅ |
| SQLite storage (`entries` table) | ✅ |
| Green dot on days that have an entry | ✅ |
| Native `.deb` installer with bundled runtime | ✅ |

---

## Foundations (built first in v1.0)

These unlock most of the roadmap:

- **JavaFX migration** ✅ — Swing UI ported to JavaFX (`JournalApp`, `CalendarView`,
  `JournalEditorDialog`), reusing `JournalDao` unchanged. `org.openjfx` deps shaded into
  the fat jar via a non-Application `Launcher`; `.deb` repackaged with the JavaFX runtime.
  CSS theming + Light/Dark toggle ✅ (external `app.css`/`dark.css`, theme in Preferences,
  applied to all windows). `WebView` Markdown / charts still to come in later v1.0 steps.
- **DB schema v2 + migrations** ✅ — versioned schema (`schema_version` + ordered steps).
  New shape: `entries(id, entry_date, title, content, mood, created_at, updated_at)`,
  `tags`, `entry_tags`, and an FTS5 `entries_fts` table (trigger-synced) for search.
  `init()` creates v2 fresh or migrates v1 in place; legacy rows preserved. Covered by
  JUnit tests (`JournalDaoTest`, 9 tests). The one-entry-per-day API is unchanged.
- **Settings store + Preferences screen** ✅ — `SettingsDao` (`settings(key, value)`,
  typed get/set, tested) with a `Settings` facade. `PreferencesDialog` (File → Preferences…)
  edits editor font size and week-start; both persist and apply live (editor font, calendar
  layout). Menu bar added to the main window.
- **Stable app data directory** ✅ — `AppPaths` stores `journal.db` under
  `~/.local/share/CalendarJournal/` (platform-appropriate) and migrates the legacy
  `~/journal.db` on first run, so data survives reinstalls.
- **Packaging update** — `jpackage` bundles JavaFX modules via `jlink`; generalize `build-deb.sh`.

---

## v1.0 — "A real daily driver" (next milestone)

| Theme | Feature |
|-------|---------|
| Foundation | JavaFX migration + CSS theming + light/dark toggle ✅ |
| Foundation | Stable app data dir + schema migrations |
| Writing | Autosave + unsaved-changes guard ✅ |
| Writing | Entry title + word/character count ✅ |
| Writing | Markdown editing with live preview ✅ |
| Organize | Full-text search across all entries ✅ |
| Organize | Agenda/list view toggle |
| Data | Manual backup & restore |
| Data | Export to Markdown + PDF |
| Polish | Preferences screen ✅ (font size, week-start; theme to follow) |
| Polish | Keyboard shortcuts + first-run welcome |

**Build order:** foundations → writing → organize → data → polish.

## v1.1 — Writing experience
- Mood / rating per entry (shown on the calendar)
- Tags on entries
- Multiple entries per day (timestamped)
- Entry templates (gratitude, daily standup, free-write)
- Distraction-free full-screen mode
- Inline images / attachments
- Spellcheck + font preferences

## v1.2 — Find & organize
- Tag browser & filtering
- Filter by mood / date range
- Year heatmap (contribution-grid of activity)
- Streaks & stats dashboard
- "On this day" (entries from this date in prior years)
- Year-in-review summary

## v1.3 — Data safety
- Scheduled auto-backup (keep last N, prune old)
- Encryption at rest (SQLCipher, opt-in)
- App password / lock screen
- Import from plain text / Markdown folders
- Import from other journals (e.g. Day One JSON)
- Full JSON export (round-trippable)

## v1.4 — Reminders & habit
- Daily reminder notification at a chosen time
- System tray icon (quick "new entry today")
- Launch-on-login option
- Habit goals & progress (target N days/week)
- Streak badges (7 / 30 / 100 days)

## v2.0 — Cross-platform ship & polish
- Windows installer (.msi/.exe)
- macOS installer (.pkg/.dmg)
- Per-platform build scripts (packaging matrix)
- Accessibility pass (font scaling, high contrast, keyboard nav)
- In-app About + version + changelog
- Friendly error handling & logging

---

## Out of scope (for now)
Cloud sync · accounts · multi-device · mobile/web clients · auto-update ·
collaboration/sharing · AI features.
