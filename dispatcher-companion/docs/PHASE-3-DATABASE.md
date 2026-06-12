# RingCentral Dispatcher Companion — Phase 3: Database Design

Room + SQLCipher (AES-256, key in Android Keystore). Schema below is the
SQL Room will generate; it is executed and validated by
`docs/validate_schema.py` against real SQLite (see Phase 3 test results).

## Entity-Relationship Overview

```
brokers 1──* calls 1──* transcript_segments
   │            1──1 load_details
   │            1──1 call_summaries
   │            1──* rate_events
zip_geo (static, bundled)         settings (key/value)        export_log
brokers_fts / calls_fts (FTS4 mirrors for search)
```

## DDL (Room schema v1)

```sql
CREATE TABLE brokers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL DEFAULT '',
  company TEXT NOT NULL DEFAULT '',
  mc_number TEXT,                          -- unique when known
  phone TEXT,
  negotiation_style TEXT,                  -- AGGRESSIVE|FIRM|FLEXIBLE|UNKNOWN
  reliability_score REAL NOT NULL DEFAULT 0,   -- 0..1 rolling
  success_rate REAL NOT NULL DEFAULT 0,        -- booked / total calls
  avg_rate_per_mile REAL,
  notes TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_brokers_mc ON brokers(mc_number) WHERE mc_number IS NOT NULL;
CREATE INDEX idx_brokers_company ON brokers(company);

CREATE TABLE calls (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  broker_id INTEGER REFERENCES brokers(id) ON DELETE SET NULL,
  started_at INTEGER NOT NULL,
  ended_at INTEGER,
  direction TEXT NOT NULL DEFAULT 'INBOUND',     -- INBOUND|OUTBOUND
  capture_method TEXT NOT NULL,                  -- MIC|PLAYBACK|VOIP|ACCESSIBILITY|PROJECTION
  capture_quality TEXT NOT NULL,                 -- EXCELLENT|GOOD|LIMITED|FALLBACK
  outcome TEXT NOT NULL DEFAULT 'UNKNOWN',       -- BOOKED|REJECTED|FOLLOW_UP|UNKNOWN
  consent_recorded INTEGER NOT NULL DEFAULT 0,   -- bool: audio retention consent
  audio_path TEXT,                               -- null in transcript-only mode
  recovered INTEGER NOT NULL DEFAULT 0           -- bool: restored after crash
);
CREATE INDEX idx_calls_broker_started ON calls(broker_id, started_at DESC);
CREATE INDEX idx_calls_started ON calls(started_at DESC);

CREATE TABLE transcript_segments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  call_id INTEGER NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
  seq INTEGER NOT NULL,                          -- monotonic per call
  speaker TEXT NOT NULL DEFAULT 'UNKNOWN',       -- BROKER|DISPATCHER|UNKNOWN
  text TEXT NOT NULL,
  t_start_ms INTEGER NOT NULL,
  t_end_ms INTEGER NOT NULL,
  asr_confidence REAL NOT NULL DEFAULT 0,
  UNIQUE(call_id, seq)
);
CREATE INDEX idx_segments_call ON transcript_segments(call_id, seq);

CREATE TABLE load_details (                      -- PDWCR + extended, 1:1 with call
  call_id INTEGER PRIMARY KEY REFERENCES calls(id) ON DELETE CASCADE,
  pickup_city TEXT,  pickup_state TEXT,  pickup_zip TEXT,
  delivery_city TEXT, delivery_state TEXT, delivery_zip TEXT,
  weight_lbs INTEGER,
  commodity TEXT,
  rate_usd REAL,                                 -- current/agreed rate
  equipment TEXT,                                -- DRY_VAN|REEFER|FLATBED|...
  length_ft INTEGER,
  appointment_pickup TEXT, appointment_delivery TEXT,
  special_requirements TEXT, detention_info TEXT, layover_info TEXT,
  loaded_miles REAL, deadhead_miles REAL, rpm REAL,
  notes TEXT NOT NULL DEFAULT '',
  field_sources TEXT NOT NULL DEFAULT '{}',      -- JSON: field -> {src, confidence, manual}
  updated_at INTEGER NOT NULL
);

CREATE TABLE rate_events (                       -- negotiation path: 1700→1900→2100
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  call_id INTEGER NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
  t_ms INTEGER NOT NULL,
  actor TEXT NOT NULL,                           -- BROKER|DISPATCHER|AI_SUGGESTION
  amount_usd REAL NOT NULL,
  kind TEXT NOT NULL                             -- OFFER|COUNTER|FLOOR_EST|CEILING_EST|AGREED
);
CREATE INDEX idx_rate_events_call ON rate_events(call_id, t_ms);

CREATE TABLE call_summaries (
  call_id INTEGER PRIMARY KEY REFERENCES calls(id) ON DELETE CASCADE,
  lane TEXT NOT NULL DEFAULT '',                 -- "Dallas, TX → Atlanta, GA"
  summary_short TEXT NOT NULL DEFAULT '',
  summary_detailed TEXT NOT NULL DEFAULT '',
  summary_bullets TEXT NOT NULL DEFAULT '',
  summary_crm TEXT NOT NULL DEFAULT '',
  key_details TEXT NOT NULL DEFAULT '',
  follow_up_actions TEXT NOT NULL DEFAULT '',
  next_steps TEXT NOT NULL DEFAULT '',
  generated_by TEXT NOT NULL DEFAULT 'LOCAL',    -- LOCAL|CLAUDE|OPENAI (queued offline → updated)
  created_at INTEGER NOT NULL
);

CREATE TABLE zip_geo (                           -- bundled static dataset (~42k rows)
  zip TEXT PRIMARY KEY,
  city TEXT NOT NULL, state TEXT NOT NULL,
  lat REAL NOT NULL, lon REAL NOT NULL
);
CREATE INDEX idx_zip_geo_city ON zip_geo(city, state);

CREATE TABLE settings (k TEXT PRIMARY KEY, v TEXT NOT NULL);
CREATE TABLE export_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  call_id INTEGER REFERENCES calls(id) ON DELETE SET NULL,
  format TEXT NOT NULL, destination TEXT NOT NULL, created_at INTEGER NOT NULL
);

-- Full-text search (FTS4 — SQLCipher-compatible)
CREATE VIRTUAL TABLE brokers_fts USING fts4(name, company, notes, content=`brokers`);
CREATE VIRTUAL TABLE calls_fts  USING fts4(text, content=`transcript_segments`);
```

## Key Design Decisions

1. **Crash-safe transcripts:** segments insert as they finalize (`UNIQUE(call_id,seq)`
   makes recovery idempotent); a killed service loses at most the in-flight partial.
2. **`field_sources` JSON column** records per-field provenance (tier-1/tier-2/manual
   + confidence) so the merger's "manual edits are sticky" rule survives restarts.
3. **`rate_events`** keeps the full negotiation path — required by the copilot
   (floor/ceiling estimation) and by broker analytics (Phase 9 rollups).
4. **Broker rollups** (`avg_rate_per_mile`, `success_rate`, `reliability_score`)
   are denormalized and recomputed by a WorkManager job after each call summary.
5. **FTS4 not FTS5:** Room's `@Fts4` is supported by SQLCipher across the Android
   range; gives "searchable history" (FR-701) at zero extra dependency cost.
6. **Backups:** nightly `VACUUM INTO` an encrypted copy in app-private storage,
   rotating 7; restore path validated in Phase 10.
7. **zip_geo** ships as a prepackaged Room database asset → ZIP↔city lookups and
   haversine-based mileage work fully offline (FR-602, FR-901).

## Phase-3 Test Gate

`validate_schema.py` executes the full DDL on real SQLite, then runs:
referential-integrity checks (cascade delete), idempotent-recovery insert test,
negotiation-path query, broker-history query, and an FTS search. Results are in
the Phase 3 report in the project log; the script is rerunnable.
