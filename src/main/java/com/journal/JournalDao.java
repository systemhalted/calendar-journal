package com.journal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * All SQLite access for journal entries. The single point of database contact —
 * no SQL lives anywhere else in the app.
 *
 * <p>Schema is versioned (see {@link #SCHEMA_VERSION}). {@link #init()} creates a
 * fresh v2 database or migrates an older one in place. The public API currently
 * keeps one entry per day; the v2 schema (entry ids, title, mood, tags, full-text
 * search) is in place to support later features.
 */
public class JournalDao {

    /** Current schema version this code understands. */
    public static final int SCHEMA_VERSION = 2;

    private final String url;

    /**
     * @param dbFile path to the SQLite database file (created on first use)
     */
    public JournalDao(String dbFile) {
        this.url = "jdbc:sqlite:" + dbFile;
    }

    private Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    // ---------------------------------------------------------------- schema

    /** Create the schema if absent, or migrate an older database up to the current version. */
    public void init() {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                if (tableExists(conn, "schema_version")) {
                    int version = readVersion(conn);
                    if (version < SCHEMA_VERSION) {
                        // Place future stepwise migrations (v2 -> v3 ...) here.
                        throw new JournalException("Unsupported schema version " + version, null);
                    }
                } else if (tableExists(conn, "entries")) {
                    migrateV1ToV2(conn);   // legacy DB: entries table, no schema_version
                } else {
                    createV2Schema(conn);  // fresh DB
                }
                conn.commit();
            } catch (SQLException | JournalException e) {
                conn.rollback();
                throw (e instanceof JournalException je) ? je
                        : new JournalException("Failed to initialize database", e);
            }
        } catch (SQLException e) {
            throw new JournalException("Failed to open database", e);
        }
    }

    /** @return the schema version recorded in the database, or 0 if none. */
    public int schemaVersion() {
        try (Connection conn = connect()) {
            return tableExists(conn, "schema_version") ? readVersion(conn) : 0;
        } catch (SQLException e) {
            throw new JournalException("Failed to read schema version", e);
        }
    }

    private void createV2Schema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE entries (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        entry_date TEXT NOT NULL,
                        title      TEXT,
                        content    TEXT NOT NULL,
                        mood       TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            st.execute("CREATE INDEX idx_entries_date ON entries(entry_date)");
        }
        createAuxObjects(conn);
    }

    private void migrateV1ToV2(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE entries RENAME TO entries_v1");
            st.execute("""
                    CREATE TABLE entries (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        entry_date TEXT NOT NULL,
                        title      TEXT,
                        content    TEXT NOT NULL,
                        mood       TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            st.execute("""
                    INSERT INTO entries (entry_date, title, content, mood, created_at, updated_at)
                    SELECT entry_date, NULL, content, NULL, updated_at, updated_at FROM entries_v1
                    """);
            st.execute("DROP TABLE entries_v1");
            st.execute("CREATE INDEX idx_entries_date ON entries(entry_date)");
        }
        createAuxObjects(conn);
    }

    /** Tags, full-text index + sync triggers, and the schema-version marker. */
    private void createAuxObjects(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS entry_tags (
                        entry_id INTEGER NOT NULL,
                        tag_id   INTEGER NOT NULL,
                        PRIMARY KEY (entry_id, tag_id),
                        FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE,
                        FOREIGN KEY (tag_id)   REFERENCES tags(id)    ON DELETE CASCADE
                    )
                    """);
            // External-content FTS5 mirrors entries(title, content), kept in sync by triggers.
            st.execute("CREATE VIRTUAL TABLE entries_fts USING fts5(title, content, content='entries', content_rowid='id')");
            st.execute("""
                    CREATE TRIGGER entries_ai AFTER INSERT ON entries BEGIN
                        INSERT INTO entries_fts(rowid, title, content) VALUES (new.id, new.title, new.content);
                    END
                    """);
            st.execute("""
                    CREATE TRIGGER entries_ad AFTER DELETE ON entries BEGIN
                        INSERT INTO entries_fts(entries_fts, rowid, title, content) VALUES ('delete', old.id, old.title, old.content);
                    END
                    """);
            st.execute("""
                    CREATE TRIGGER entries_au AFTER UPDATE ON entries BEGIN
                        INSERT INTO entries_fts(entries_fts, rowid, title, content) VALUES ('delete', old.id, old.title, old.content);
                        INSERT INTO entries_fts(rowid, title, content) VALUES (new.id, new.title, new.content);
                    END
                    """);
            // Populate the index from any rows that predate the triggers (migration path).
            st.execute("INSERT INTO entries_fts(entries_fts) VALUES('rebuild')");

            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            st.execute("DELETE FROM schema_version");
            st.execute("INSERT INTO schema_version(version) VALUES (" + SCHEMA_VERSION + ")");
        }
    }

    private boolean tableExists(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int readVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ---------------------------------------------------------------- entries

    /** @return the saved content for the date, or {@code null} if none exists. */
    public String load(LocalDate date) {
        String sql = "SELECT content FROM entries WHERE entry_date = ? ORDER BY id LIMIT 1";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("content") : null;
            }
        } catch (SQLException e) {
            throw new JournalException("Failed to load entry for " + date, e);
        }
    }

    /** Insert or update the (single) entry for the given date. */
    public void save(LocalDate date, String content) {
        String now = LocalDateTime.now().toString();
        try (Connection conn = connect()) {
            Integer id = findEntryId(conn, date);
            if (id != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE entries SET content = ?, updated_at = ? WHERE id = ?")) {
                    ps.setString(1, content);
                    ps.setString(2, now);
                    ps.setInt(3, id);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO entries (entry_date, content, created_at, updated_at) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, date.toString());
                    ps.setString(2, content);
                    ps.setString(3, now);
                    ps.setString(4, now);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new JournalException("Failed to save entry for " + date, e);
        }
    }

    /** @return the entry (title + content) for the date, or {@code null} if none exists. */
    public Entry loadEntry(LocalDate date) {
        String sql = "SELECT title, content FROM entries WHERE entry_date = ? ORDER BY id LIMIT 1";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Entry(rs.getString("title"), rs.getString("content")) : null;
            }
        } catch (SQLException e) {
            throw new JournalException("Failed to load entry for " + date, e);
        }
    }

    /** Insert or update the (single) entry for the given date, including its title. */
    public void saveEntry(LocalDate date, String title, String content) {
        String now = LocalDateTime.now().toString();
        try (Connection conn = connect()) {
            Integer id = findEntryId(conn, date);
            if (id != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE entries SET title = ?, content = ?, updated_at = ? WHERE id = ?")) {
                    ps.setString(1, title);
                    ps.setString(2, content);
                    ps.setString(3, now);
                    ps.setInt(4, id);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO entries (entry_date, title, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, date.toString());
                    ps.setString(2, title);
                    ps.setString(3, content);
                    ps.setString(4, now);
                    ps.setString(5, now);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new JournalException("Failed to save entry for " + date, e);
        }
    }

    private Integer findEntryId(Connection conn, LocalDate date) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM entries WHERE entry_date = ? ORDER BY id LIMIT 1")) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /** Remove the entry/entries for the given date (no-op if absent). */
    public void delete(LocalDate date) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM entries WHERE entry_date = ?")) {
            ps.setString(1, date.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new JournalException("Failed to delete entry for " + date, e);
        }
    }

    /** @return the set of dates within the month that have a saved entry. */
    public Set<LocalDate> datesWithEntries(YearMonth month) {
        String sql = "SELECT DISTINCT entry_date FROM entries WHERE entry_date >= ? AND entry_date <= ?";
        Set<LocalDate> dates = new HashSet<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, month.atDay(1).toString());
            ps.setString(2, month.atEndOfMonth().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dates.add(LocalDate.parse(rs.getString("entry_date")));
                }
            }
        } catch (SQLException e) {
            throw new JournalException("Failed to query entries for " + month, e);
        }
        return dates;
    }

    /**
     * Full-text search over entry titles and content.
     *
     * @return dates of matching entries, ordered chronologically.
     */
    public List<LocalDate> search(String query) {
        String sql = """
                SELECT e.entry_date
                FROM entries_fts
                JOIN entries e ON e.id = entries_fts.rowid
                WHERE entries_fts MATCH ?
                ORDER BY e.entry_date
                """;
        List<LocalDate> hits = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, query);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hits.add(LocalDate.parse(rs.getString("entry_date")));
                }
            }
        } catch (SQLException e) {
            throw new JournalException("Failed to search for \"" + query + "\"", e);
        }
        return hits;
    }

    /**
     * Full-text search returning richer results: date, title, and a snippet of
     * matching text, ordered by relevance.
     */
    public List<SearchHit> searchHits(String query) {
        String sql = """
                SELECT e.entry_date,
                       e.title,
                       snippet(entries_fts, -1, '', '', '…', 12) AS snip
                FROM entries_fts
                JOIN entries e ON e.id = entries_fts.rowid
                WHERE entries_fts MATCH ?
                ORDER BY rank
                """;
        List<SearchHit> hits = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, query);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hits.add(new SearchHit(
                            LocalDate.parse(rs.getString("entry_date")),
                            rs.getString("title"),
                            rs.getString("snip")));
                }
            }
        } catch (SQLException e) {
            throw new JournalException("Failed to search for \"" + query + "\"", e);
        }
        return hits;
    }

    /** Unchecked wrapper so UI code can surface DB errors without checked-exception noise. */
    public static class JournalException extends RuntimeException {
        public JournalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
