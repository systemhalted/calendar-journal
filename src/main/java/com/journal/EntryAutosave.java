package com.journal;

import java.time.LocalDate;

/**
 * The autosave persistence policy for a day's entry: if both title and content
 * are blank the entry is removed; otherwise it is upserted (a blank title is
 * stored as {@code null}). Kept separate from the editor UI so the rule can be
 * tested without the JavaFX toolkit.
 */
public final class EntryAutosave {

    private EntryAutosave() {
    }

    /** Persist the title/content for {@code date}: upsert if either has text, else delete. */
    public static void persist(JournalDao dao, LocalDate date, String title, String content) {
        if (isBlank(title) && isBlank(content)) {
            dao.delete(date);
        } else {
            dao.saveEntry(date, isBlank(title) ? null : title, content);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
