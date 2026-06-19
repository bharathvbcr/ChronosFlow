package com.ChronosFlow.VBCR.core.data

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.room.RoomDatabase
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
        if (!ENCRYPTION_ENABLED) return builder.build()
        System.loadLibrary("sqlcipher")
        return builder
            .openHelperFactory(SupportOpenHelperFactory(obtainPassphraseBytes()))
            .build()
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
            return decryptPassphrase(
                cipherText = Base64.decode(encrypted, Base64.NO_WRAP),
                iv = Base64.decode(iv, Base64.NO_WRAP)
            )
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
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private data class EncryptedPassphrase(
        val cipherText: ByteArray,
        val iv: ByteArray
    )

    companion object {
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
