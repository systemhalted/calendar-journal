package com.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalDaoTest {

    @TempDir
    Path tmp;

    private JournalDao freshDao() {
        JournalDao dao = new JournalDao(tmp.resolve("journal.db").toString());
        dao.init();
        return dao;
    }

    @Test
    void freshDatabaseIsAtSchemaVersion2() {
        assertEquals(2, freshDao().schemaVersion());
    }

    @Test
    void saveThenLoadReturnsContent() {
        JournalDao dao = freshDao();
        LocalDate d = LocalDate.of(2026, 6, 9);
        assertNull(dao.load(d));
        dao.save(d, "first light");
        assertEquals("first light", dao.load(d));
    }

    @Test
    void savingSameDayTwiceKeepsOneEntry() {
        JournalDao dao = freshDao();
        LocalDate d = LocalDate.of(2026, 6, 9);
        dao.save(d, "draft");
        dao.save(d, "final");
        assertEquals("final", dao.load(d));
        assertEquals(1, dao.datesWithEntries(YearMonth.of(2026, 6)).size());
    }

    @Test
    void deleteRemovesEntry() {
        JournalDao dao = freshDao();
        LocalDate d = LocalDate.of(2026, 6, 9);
        dao.save(d, "to be removed");
        dao.delete(d);
        assertNull(dao.load(d));
    }

    @Test
    void datesWithEntriesIsScopedToTheMonth() {
        JournalDao dao = freshDao();
        dao.save(LocalDate.of(2026, 6, 9), "june");
        dao.save(LocalDate.of(2026, 7, 1), "july");
        Set<LocalDate> june = dao.datesWithEntries(YearMonth.of(2026, 6));
        assertEquals(Set.of(LocalDate.of(2026, 6, 9)), june);
    }

    @Test
    void searchFindsEntryByContentWord() {
        JournalDao dao = freshDao();
        LocalDate d = LocalDate.of(2026, 6, 9);
        dao.save(d, "Today I rebuilt my childhood project.");
        dao.save(LocalDate.of(2026, 6, 10), "Unrelated thoughts about lunch.");
        List<LocalDate> hits = dao.search("childhood");
        assertEquals(List.of(d), hits);
    }

    @Test
    void searchReflectsUpdatesAndDeletes() {
        JournalDao dao = freshDao();
        LocalDate d = LocalDate.of(2026, 6, 9);
        dao.save(d, "mentions kayaking");
        assertEquals(List.of(d), dao.search("kayaking"));
        dao.save(d, "now mentions hiking instead");   // FTS must follow updates
        assertTrue(dao.search("kayaking").isEmpty());
        assertEquals(List.of(d), dao.search("hiking"));
        dao.delete(d);                                  // and deletes
        assertTrue(dao.search("hiking").isEmpty());
    }

    @Test
    void migratesLegacyV1DatabasePreservingEntriesAndMakingThemSearchable() throws Exception {
        Path dbPath = tmp.resolve("legacy.db");
        // Build a v1-shaped database by hand (no schema_version table).
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE entries (entry_date TEXT PRIMARY KEY, content TEXT NOT NULL, updated_at TEXT NOT NULL)");
            st.execute("INSERT INTO entries VALUES ('2025-12-25', 'A snowy childhood memory', '2025-12-25T10:00:00')");
        }

        JournalDao dao = new JournalDao(dbPath.toString());
        dao.init();

        assertEquals(2, dao.schemaVersion());
        assertEquals("A snowy childhood memory", dao.load(LocalDate.of(2025, 12, 25)));
        assertEquals(List.of(LocalDate.of(2025, 12, 25)), dao.search("childhood"));
    }

    @Test
    void saveEntryRoundTripsTitleAndContent() {
        JournalDao dao = freshDao();
        LocalDate d = LocalDate.of(2026, 6, 9);
        dao.saveEntry(d, "My Title", "the body");
        Entry e = dao.loadEntry(d);
        assertEquals("My Title", e.title());
        assertEquals("the body", e.content());
    }

    @Test
    void loadEntryReturnsNullWhenAbsent() {
        assertNull(freshDao().loadEntry(LocalDate.of(2026, 6, 9)));
    }

    @Test
    void saveEntryUpdatesInPlaceKeepingOneEntryPerDay() {
        JournalDao dao = freshDao();
        LocalDate d = LocalDate.of(2026, 6, 9);
        dao.saveEntry(d, "t1", "c1");
        dao.saveEntry(d, "t2", "c2");
        Entry e = dao.loadEntry(d);
        assertEquals("t2", e.title());
        assertEquals("c2", e.content());
        assertEquals(1, dao.datesWithEntries(YearMonth.of(2026, 6)).size());
    }

    @Test
    void searchMatchesTitleWords() {
        JournalDao dao = freshDao();
        LocalDate d = LocalDate.of(2026, 6, 9);
        dao.saveEntry(d, "Kayaking trip", "we paddled all day");
        assertEquals(List.of(d), dao.search("Kayaking"));
    }

    @Test
    void initIsIdempotent() {
        JournalDao dao = freshDao();
        dao.save(LocalDate.of(2026, 6, 9), "kept across re-init");
        dao.init();   // running again must not wipe data or change version
        assertEquals(2, dao.schemaVersion());
        assertEquals("kept across re-init", dao.load(LocalDate.of(2026, 6, 9)));
        assertFalse(dao.datesWithEntries(YearMonth.of(2026, 6)).isEmpty());
    }
}
