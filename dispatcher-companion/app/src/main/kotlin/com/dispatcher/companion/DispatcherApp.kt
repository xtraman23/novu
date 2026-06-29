package com.dispatcher.companion

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dispatcher.companion.calculator.FreightCalculator
import com.dispatcher.companion.db.DispatcherDb
import com.dispatcher.companion.geo.AssetGeoIndex
import com.dispatcher.companion.session.DispatchSession
import java.security.SecureRandom

class DispatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}

/** Manual DI — one graph, created once per process. */
object ServiceLocator {
    lateinit var db: DispatcherDb
        private set
    lateinit var session: DispatchSession
        private set
    lateinit var calculator: FreightCalculator
        private set

    fun init(context: Context) {
        if (::db.isInitialized) return
        db = DispatcherDb(context, dbPassphrase(context))
        session = DispatchSession(db)
        calculator = FreightCalculator(AssetGeoIndex(context))
    }

    /** SQLCipher key: generated once, stored in EncryptedSharedPreferences (Keystore-backed). */
    private fun dbPassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context, "secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val existing = prefs.getString("db_key", null)
        if (existing != null) return existing.toByteArray(Charsets.ISO_8859_1)
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("db_key", String(key, Charsets.ISO_8859_1)).apply()
        return key
    }
}
