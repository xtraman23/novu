package com.dispatcher.companion.db

import android.content.Context
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.CallSummary
import com.dispatcher.companion.model.CaptureMethodId
import com.dispatcher.companion.model.CaptureQuality
import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldValue
import com.dispatcher.companion.model.TranscriptSegment
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

/**
 * Encrypted local store (FR-902): SQLCipher AES-256; the passphrase is
 * created once and held in EncryptedSharedPreferences by the app module.
 * Schema is the validated Phase 3 DDL.
 */
class DispatcherDb(context: Context, passphrase: ByteArray) :
    SQLiteOpenHelper(context, "dispatcher.db", null, Schema.VERSION) {

    init {
        SQLiteDatabase.loadLibs(context)
    }

    private val db: SQLiteDatabase by lazy { getWritableDatabase(passphrase) }

    override fun onCreate(db: SQLiteDatabase) {
        Schema.DDL.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit // v1

    override fun onOpen(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=ON")
    }

    // --- calls ---------------------------------------------------------------

    fun startCall(method: CaptureMethodId, quality: CaptureQuality, brokerId: Long? = null): Long {
        db.execSQL(
            "INSERT INTO calls(broker_id, started_at, capture_method, capture_quality) VALUES(?,?,?,?)",
            arrayOf(brokerId, System.currentTimeMillis(), method.name, quality.name),
        )
        return lastId()
    }

    fun endCall(callId: Long, outcome: String) {
        db.execSQL(
            "UPDATE calls SET ended_at=?, outcome=? WHERE id=?",
            arrayOf(System.currentTimeMillis(), outcome, callId),
        )
    }

    /** Crash-safe + idempotent (UNIQUE call_id, seq). */
    fun insertSegment(callId: Long, s: TranscriptSegment) {
        db.execSQL(
            "INSERT OR IGNORE INTO transcript_segments" +
                "(call_id, seq, speaker, text, t_start_ms, t_end_ms, asr_confidence) VALUES(?,?,?,?,?,?,?)",
            arrayOf(callId, s.seq, s.speaker.name, s.text, s.tStartMs, s.tEndMs, s.confidence),
        )
    }

    fun lastSegmentSeq(callId: Long): Int =
        db.rawQuery("SELECT COALESCE(MAX(seq),0) FROM transcript_segments WHERE call_id=?", arrayOf(callId.toString()))
            .use { it.moveToFirst(); it.getInt(0) }

    fun insertRateEvent(callId: Long, e: RateEvent) {
        db.execSQL(
            "INSERT INTO rate_events(call_id, t_ms, actor, amount_usd, kind) VALUES(?,?,?,?,?)",
            arrayOf(callId, e.tMs, e.actor.name, e.amountUsd, e.kind.name),
        )
    }

    fun saveLoadFields(callId: Long, fields: Map<FieldKey, FieldValue>) {
        db.execSQL("INSERT OR IGNORE INTO load_details(call_id, updated_at) VALUES(?,?)", arrayOf(callId, System.currentTimeMillis()))
        fun v(k: FieldKey) = fields[k]?.text
        db.execSQL(
            """UPDATE load_details SET pickup_city=?, delivery_city=?, pickup_zip=?, delivery_zip=?,
               weight_lbs=?, commodity=?, rate_usd=?, equipment=?, length_ft=?,
               appointment_pickup=?, appointment_delivery=?, special_requirements=?,
               detention_info=?, layover_info=?, updated_at=? WHERE call_id=?""",
            arrayOf(
                v(FieldKey.PICKUP), v(FieldKey.DELIVERY), v(FieldKey.PICKUP_ZIP), v(FieldKey.DELIVERY_ZIP),
                v(FieldKey.WEIGHT)?.filter { it.isDigit() }?.toLongOrNull(),
                v(FieldKey.COMMODITY),
                v(FieldKey.RATE)?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull(),
                v(FieldKey.EQUIPMENT), v(FieldKey.LENGTH_FT)?.toIntOrNull(),
                v(FieldKey.APPOINTMENT_PICKUP), v(FieldKey.APPOINTMENT_DELIVERY),
                v(FieldKey.SPECIAL_REQUIREMENTS), v(FieldKey.DETENTION), v(FieldKey.LAYOVER),
                System.currentTimeMillis(), callId,
            ),
        )
    }

    fun saveSummary(callId: Long, s: CallSummary, generatedBy: String) {
        db.execSQL(
            """INSERT OR REPLACE INTO call_summaries(call_id, lane, summary_short, summary_detailed,
               summary_bullets, summary_crm, key_details, follow_up_actions, next_steps,
               generated_by, created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
            arrayOf(
                callId, s.lane, s.summaryShort, s.summaryDetailed, s.summaryBullets,
                s.summaryCrm, s.keyDetails, s.followUpActions, s.nextSteps,
                generatedBy, System.currentTimeMillis(),
            ),
        )
    }

    // --- brokers -------------------------------------------------------------

    fun upsertBroker(name: String, company: String, mcNumber: String?): Long {
        if (mcNumber != null) {
            db.rawQuery("SELECT id FROM brokers WHERE mc_number=?", arrayOf(mcNumber)).use {
                if (it.moveToFirst()) return it.getLong(0)
            }
        }
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO brokers(name, company, mc_number, created_at, updated_at) VALUES(?,?,?,?,?)",
            arrayOf(name, company, mcNumber, now, now),
        )
        return lastId()
    }

    fun searchBrokers(query: String): List<Triple<Long, String, String>> =
        db.rawQuery(
            "SELECT id, name, company FROM brokers WHERE name LIKE ? OR company LIKE ? ORDER BY updated_at DESC LIMIT 50",
            arrayOf("%$query%", "%$query%"),
        ).use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) }
        }

    private fun lastId(): Long =
        db.rawQuery("SELECT last_insert_rowid()", emptyArray()).use { it.moveToFirst(); it.getLong(0) }
}
