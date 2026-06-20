package com.ChronosFlow.VBCR.core.data

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Creates SQLCipher-backed Room databases with a passphrase protected by Android Keystore.
 */
@Singleton
class ChronosSecureDatabaseProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun create(builder: RoomDatabase.Builder<ChronosDatabase>): ChronosDatabase {
        // Write-Ahead Logging lets readers run concurrently with a writer. Without it the
        // SQLCipher SupportOpenHelperFactory opens a single serialized connection, so a write
        // (delete/duplicate/undo a block) blocks the reactive read that repaints the timeline —
        // which is why those edits used to take "forever" to appear. The app is single-process
        // (no android:process), so default single-instance invalidation stays correct under WAL.
        builder.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        builder.addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        })
        if (!ENCRYPTION_ENABLED) return builder.build()
        System.loadLibrary("sqlcipher")
        val passphrase = obtainPassphraseBytes()
        return try {
            builder
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        } finally {
            // Zero the passphrase immediately after handing it to the factory so it
            // does not linger on the heap where a heap dump (rooted device) could recover it.
            passphrase.fill(0)
        }
    }

    internal fun obtainPassphraseBytes(): ByteArray = databasePassphrase()

    fun databaseBuilder(
        name: String = DATABASE_NAME,
        migrations: Array<androidx.room.migration.Migration> = emptyArray()
    ): RoomDatabase.Builder<ChronosDatabase> {
        val builder = Room.databaseBuilder(context, ChronosDatabase::class.java, resolvedName(name))
        migrations.forEach { builder.addMigrations(it) }
        return builder
    }

    fun resolvedName(baseName: String): String =
        if (ENCRYPTION_ENABLED) "${baseName}_encrypted" else baseName

    private fun databasePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(PREF_KEY_ENCRYPTED_PASSPHRASE, null)
        val iv = prefs.getString(PREF_KEY_IV, null)
        if (encrypted != null && iv != null) {
            try {
                return decryptPassphrase(
                    cipherText = Base64.decode(encrypted, Base64.NO_WRAP),
                    iv = Base64.decode(iv, Base64.NO_WRAP)
                )
            } catch (e: KeyPermanentlyInvalidatedException) {
                // The Keystore key was invalidated — most commonly because the device's enrolled
                // biometrics or screen-lock credential changed. The stored passphrase is now
                // unrecoverable; we must discard it and generate a fresh key + passphrase.
                // This means the encrypted database cannot be opened with the old passphrase;
                // the caller (Room builder) will see a SQLCipher "file is not a database" error
                // and the app should guide the user to reset their data.
                Log.w(TAG, "Keystore key permanently invalidated — regenerating DB passphrase.", e)
                prefs.edit()
                    .remove(PREF_KEY_ENCRYPTED_PASSPHRASE)
                    .remove(PREF_KEY_IV)
                    .apply()
                // Fall through to generate a fresh passphrase below.
            }
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(passphrase)
        val encryptedPassphrase = encryptPassphrase(passphrase)
        prefs.edit()
            .putString(
                PREF_KEY_ENCRYPTED_PASSPHRASE,
                Base64.encodeToString(encryptedPassphrase.cipherText, Base64.NO_WRAP)
            )
            .putString(PREF_KEY_IV, Base64.encodeToString(encryptedPassphrase.iv, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private fun encryptPassphrase(passphrase: ByteArray): EncryptedPassphrase {
        val cipher = Cipher.getInstance(KEY_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return EncryptedPassphrase(
            cipherText = cipher.doFinal(passphrase),
            iv = cipher.iv
        )
    }

    private fun decryptPassphrase(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(KEY_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            // Do NOT set setUserAuthenticationRequired(true) here.
            // AES/GCM Keystore encryption already protects the passphrase at rest — the key
            // is hardware-bound and never leaves the Keystore. Adding a user-auth constraint
            // breaks every background worker that cold-starts after the screen has been locked
            // for more than 5 minutes (InteropSyncWorker every 6h, HealthConnectSyncWorker,
            // WidgetUpdateWorker, DayPlanBackupWorker) because Cipher.init throws
            // UserNotAuthenticatedException before the work even begins.
            // Interactive auth is enforced at the UI layer via SensitiveRouteGate, which is
            // the correct place for that requirement.
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private data class EncryptedPassphrase(
        val cipherText: ByteArray,
        val iv: ByteArray
    )

    companion object {
        private const val TAG = "ChronosSecureDatabaseProvider"
        const val DATABASE_NAME = "chronos_db"
        const val ENCRYPTION_ENABLED = true

        private const val PREFS_NAME = "chronos_secure_database"
        private const val PREF_KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
        private const val PREF_KEY_IV = "passphrase_iv"
        private const val PASSPHRASE_BYTES = 32
        private const val GCM_TAG_BITS = 128
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "chronosflow_room_sqlcipher"
        private const val KEY_ALGORITHM = "AES"
        private const val KEY_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
