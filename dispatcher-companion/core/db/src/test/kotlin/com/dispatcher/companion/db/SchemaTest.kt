package com.dispatcher.companion.db

import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Executes the embedded Schema DDL on real SQLite (JVM) — keeps it in sync
 *  with the validated Phase 3 schema without needing a device. */
class SchemaTest {

    @Test
    fun `embedded DDL executes and core invariants hold`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute("PRAGMA foreign_keys=ON")
                Schema.DDL.forEach { st.execute(it) }

                st.execute("INSERT INTO brokers(name, company, created_at, updated_at) VALUES('M','TQL',1,1)")
                st.execute("INSERT INTO calls(broker_id, started_at, capture_method, capture_quality) VALUES(1,1,'MIC','GOOD')")
                st.execute("INSERT OR IGNORE INTO transcript_segments(call_id,seq,speaker,text,t_start_ms,t_end_ms) VALUES(1,1,'BROKER','a',0,1)")
                st.execute("INSERT OR IGNORE INTO transcript_segments(call_id,seq,speaker,text,t_start_ms,t_end_ms) VALUES(1,1,'BROKER','a',0,1)")
                st.executeQuery("SELECT COUNT(*) FROM transcript_segments").use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1)) // idempotent recovery
                }
                st.execute("DELETE FROM calls WHERE id=1")
                st.executeQuery("SELECT COUNT(*) FROM transcript_segments").use { rs ->
                    rs.next()
                    assertEquals(0, rs.getInt(1)) // cascade
                }
                st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table'").use { rs ->
                    rs.next()
                    assertTrue(rs.getInt(1) >= 9, "expected all tables created")
                }
            }
        }
    }
}
