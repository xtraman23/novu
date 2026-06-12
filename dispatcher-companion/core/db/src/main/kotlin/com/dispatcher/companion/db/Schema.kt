package com.dispatcher.companion.db

/**
 * Schema v1 — must stay in sync with docs/PHASE-3-DATABASE.md (validated by
 * docs/validate_schema.py and the JVM test in this module).
 */
object Schema {
    const val VERSION = 1

    val DDL: List<String> = listOf(
        """CREATE TABLE brokers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL DEFAULT '',
            company TEXT NOT NULL DEFAULT '',
            mc_number TEXT,
            phone TEXT,
            negotiation_style TEXT,
            reliability_score REAL NOT NULL DEFAULT 0,
            success_rate REAL NOT NULL DEFAULT 0,
            avg_rate_per_mile REAL,
            notes TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL)""",
        "CREATE UNIQUE INDEX idx_brokers_mc ON brokers(mc_number) WHERE mc_number IS NOT NULL",
        "CREATE INDEX idx_brokers_company ON brokers(company)",
        """CREATE TABLE calls (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            broker_id INTEGER REFERENCES brokers(id) ON DELETE SET NULL,
            started_at INTEGER NOT NULL,
            ended_at INTEGER,
            direction TEXT NOT NULL DEFAULT 'INBOUND',
            capture_method TEXT NOT NULL,
            capture_quality TEXT NOT NULL,
            outcome TEXT NOT NULL DEFAULT 'UNKNOWN',
            consent_recorded INTEGER NOT NULL DEFAULT 0,
            audio_path TEXT,
            recovered INTEGER NOT NULL DEFAULT 0)""",
        "CREATE INDEX idx_calls_broker_started ON calls(broker_id, started_at DESC)",
        "CREATE INDEX idx_calls_started ON calls(started_at DESC)",
        """CREATE TABLE transcript_segments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            call_id INTEGER NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
            seq INTEGER NOT NULL,
            speaker TEXT NOT NULL DEFAULT 'UNKNOWN',
            text TEXT NOT NULL,
            t_start_ms INTEGER NOT NULL,
            t_end_ms INTEGER NOT NULL,
            asr_confidence REAL NOT NULL DEFAULT 0,
            UNIQUE(call_id, seq))""",
        "CREATE INDEX idx_segments_call ON transcript_segments(call_id, seq)",
        """CREATE TABLE load_details (
            call_id INTEGER PRIMARY KEY REFERENCES calls(id) ON DELETE CASCADE,
            pickup_city TEXT, pickup_state TEXT, pickup_zip TEXT,
            delivery_city TEXT, delivery_state TEXT, delivery_zip TEXT,
            weight_lbs INTEGER,
            commodity TEXT,
            rate_usd REAL,
            equipment TEXT,
            length_ft INTEGER,
            appointment_pickup TEXT, appointment_delivery TEXT,
            special_requirements TEXT, detention_info TEXT, layover_info TEXT,
            loaded_miles REAL, deadhead_miles REAL, rpm REAL,
            notes TEXT NOT NULL DEFAULT '',
            field_sources TEXT NOT NULL DEFAULT '{}',
            updated_at INTEGER NOT NULL)""",
        """CREATE TABLE rate_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            call_id INTEGER NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
            t_ms INTEGER NOT NULL,
            actor TEXT NOT NULL,
            amount_usd REAL NOT NULL,
            kind TEXT NOT NULL)""",
        "CREATE INDEX idx_rate_events_call ON rate_events(call_id, t_ms)",
        """CREATE TABLE call_summaries (
            call_id INTEGER PRIMARY KEY REFERENCES calls(id) ON DELETE CASCADE,
            lane TEXT NOT NULL DEFAULT '',
            summary_short TEXT NOT NULL DEFAULT '',
            summary_detailed TEXT NOT NULL DEFAULT '',
            summary_bullets TEXT NOT NULL DEFAULT '',
            summary_crm TEXT NOT NULL DEFAULT '',
            key_details TEXT NOT NULL DEFAULT '',
            follow_up_actions TEXT NOT NULL DEFAULT '',
            next_steps TEXT NOT NULL DEFAULT '',
            generated_by TEXT NOT NULL DEFAULT 'LOCAL',
            created_at INTEGER NOT NULL)""",
        """CREATE TABLE zip_geo (
            zip TEXT PRIMARY KEY,
            city TEXT NOT NULL, state TEXT NOT NULL,
            lat REAL NOT NULL, lon REAL NOT NULL)""",
        "CREATE INDEX idx_zip_geo_city ON zip_geo(city, state)",
        "CREATE TABLE settings (k TEXT PRIMARY KEY, v TEXT NOT NULL)",
        """CREATE TABLE export_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            call_id INTEGER REFERENCES calls(id) ON DELETE SET NULL,
            format TEXT NOT NULL, destination TEXT NOT NULL, created_at INTEGER NOT NULL)""",
    )
}
