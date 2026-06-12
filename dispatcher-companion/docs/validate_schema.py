#!/usr/bin/env python3
"""Phase 3 test gate: executes the DDL from PHASE-3-DATABASE.md on real SQLite
and runs integrity/recovery/search checks. Exit code 0 = schema valid."""
import re
import sqlite3
import sys
import time
from pathlib import Path

md = Path(__file__).with_name("PHASE-3-DATABASE.md").read_text()
ddl = re.search(r"```sql\n(.*?)```", md, re.S).group(1)

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
checks, failed = [], False


def check(name, cond):
    global failed
    checks.append((name, cond))
    failed |= not cond


db.executescript(ddl)
check("DDL executes cleanly (all tables, indexes, FTS4)", True)

now = int(time.time() * 1000)
db.execute("INSERT INTO brokers(name, company, mc_number, created_at, updated_at) "
           "VALUES('Mark Reynolds','TQL','322734',?,?)", (now, now))
bid = db.execute("SELECT id FROM brokers").fetchone()[0]
db.execute("INSERT INTO calls(broker_id, started_at, capture_method, capture_quality) "
           "VALUES(?,?,'MIC','GOOD')", (bid, now))
cid = db.execute("SELECT id FROM calls").fetchone()[0]

# transcript + idempotent recovery (same (call_id, seq) re-inserted after "crash")
seg = ("INSERT OR IGNORE INTO transcript_segments"
       "(call_id, seq, speaker, text, t_start_ms, t_end_ms, asr_confidence) VALUES(?,?,?,?,?,?,?)")
db.execute(seg, (cid, 1, "BROKER", "I only have $1,700 in it", 0, 2100, 0.93))
db.execute(seg, (cid, 2, "DISPATCHER", "I can move it today for $2,100", 2500, 4400, 0.91))
db.execute(seg, (cid, 1, "BROKER", "I only have $1,700 in it", 0, 2100, 0.93))  # replay
n = db.execute("SELECT COUNT(*) FROM transcript_segments WHERE call_id=?", (cid,)).fetchone()[0]
check("Idempotent segment recovery (UNIQUE call_id+seq)", n == 2)

db.execute("INSERT INTO load_details(call_id, pickup_city, pickup_state, delivery_city, "
           "delivery_state, weight_lbs, commodity, rate_usd, updated_at) "
           "VALUES(?,?,?,?,?,?,?,?,?)", (cid, "Dallas", "TX", "Atlanta", "GA", 42000, "Dry Goods", 1900, now))
for t, actor, amt, kind in [(1000, "BROKER", 1700, "OFFER"), (5000, "AI_SUGGESTION", 2100, "COUNTER"),
                            (9000, "BROKER", 1900, "OFFER"), (12000, "DISPATCHER", 2000, "AGREED")]:
    db.execute("INSERT INTO rate_events(call_id,t_ms,actor,amount_usd,kind) VALUES(?,?,?,?,?)",
               (cid, t, actor, amt, kind))
path = [r[0] for r in db.execute(
    "SELECT amount_usd FROM rate_events WHERE call_id=? ORDER BY t_ms", (cid,))]
check("Negotiation path query ordered", path == [1700, 2100, 1900, 2000])

# broker history query uses the composite index
plan = db.execute("EXPLAIN QUERY PLAN SELECT * FROM calls WHERE broker_id=? "
                  "ORDER BY started_at DESC", (bid,)).fetchall()
check("Broker-history query uses idx_calls_broker_started",
      any("idx_calls_broker_started" in str(r) for r in plan))

# FTS search over transcript (external-content FTS4: insert with docid)
for rowid, text in db.execute("SELECT id, text FROM transcript_segments"):
    db.execute("INSERT INTO calls_fts(docid, text) VALUES(?,?)", (rowid, text))
# Note: FTS4 simple tokenizer splits "$2,100" into 2/100 — numeric rate search
# must go through rate_events, not FTS. FTS is for words (broker, city, commodity).
hit = db.execute("SELECT COUNT(*) FROM calls_fts WHERE calls_fts MATCH 'today'").fetchone()[0]
check("FTS4 transcript word search matches", hit == 1)

# cascade delete
db.execute("DELETE FROM calls WHERE id=?", (cid,))
orphans = sum(db.execute(f"SELECT COUNT(*) FROM {t} WHERE call_id=?", (cid,)).fetchone()[0]
              for t in ("transcript_segments", "load_details", "rate_events"))
check("ON DELETE CASCADE removes segments/load/rate_events", orphans == 0)

# broker survives with mc uniqueness enforced
try:
    db.execute("INSERT INTO brokers(name, company, mc_number, created_at, updated_at) "
               "VALUES('Dup','X','322734',?,?)", (now, now))
    check("Unique MC index rejects duplicate", False)
except sqlite3.IntegrityError:
    check("Unique MC index rejects duplicate", True)

for name, ok in checks:
    print(("PASS  " if ok else "FAIL  ") + name)
sys.exit(1 if failed else 0)
