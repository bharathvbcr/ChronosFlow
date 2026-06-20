package com.ChronosFlow.VBCR.interop

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import com.ChronosFlow.VBCR.BuildConfig
import java.security.MessageDigest

/**
 * Gatekeeper for [InteropProvider]. The provider is exported with no platform permission (a
 * `signature` permission only works when both APKs share one signing key, which we can't assume),
 * so every query is authorized here in code instead:
 *
 *  1. The calling package must be a known peer in [InteropContract.TRUSTED_PEERS].
 *  2. Its signing certificate SHA-256 must be one of that peer's pinned certs.
 *
 * If a peer has an empty pin set we fall back to trust-on-first-use (package name only) and log the
 * observed hash so it can be pinned — that path is for bring-up, not for shipping. Anything that
 * fails a check raises [SecurityException].
 */
object PeerVerifier {
    private const val TAG = "InteropPeerVerifier"

    fun requireTrusted(context: Context, callingPackage: String?) {
        if (callingPackage.isNullOrBlank()) {
            throw SecurityException("Interop: missing calling package")
        }
        val peer = InteropContract.TRUSTED_PEERS.firstOrNull { it.packageName == callingPackage }
            ?: throw SecurityException("Interop: '$callingPackage' is not a trusted peer")

        val actual = signingSha256(context, callingPackage)
            ?: throw SecurityException("Interop: cannot read signing certificate for $callingPackage")

        if (peer.certSha256.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "No cert pinned for $callingPackage (debug only). Observed SHA-256: $actual")
                return
            } else {
                throw SecurityException(
                    "Interop: no cert pinned for $callingPackage in release build — refusing access"
                )
            }
        }
        if (peer.certSha256.none { certBytesEqual(actual, it) }) {
            throw SecurityException("Interop: signing certificate not pinned for $callingPackage")
        }
    }

    /**
     * Client-side counterpart of [requireTrusted]: returns whether [packageName] is a pinned peer.
     * Used to vet the package that actually owns a provider authority before we query it, so we
     * never hand data to (or read data from) an app squatting the peer's authority.
     */
    fun isTrusted(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val peer = InteropContract.TRUSTED_PEERS.firstOrNull { it.packageName == packageName }
            ?: return false
        val actual = signingSha256(context, packageName) ?: return false
        if (peer.certSha256.isEmpty()) return false
        return peer.certSha256.any { certBytesEqual(actual, it) }
    }

    private fun hexToBytes(hex: String): ByteArray =
        normalize(hex).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun certBytesEqual(hexA: String, hexB: String): Boolean =
        MessageDigest.isEqual(hexToBytes(hexA), hexToBytes(hexB))

    /** Canonicalize a SHA-256 hash so colons, whitespace and case don't affect comparison. */
    private fun normalize(hash: String): String =
        hash.filterNot { it == ':' || it.isWhitespace() }.uppercase()

    private fun signingSha256(context: Context, pkg: String): String? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signing = info.signingInfo ?: return null
                // Reject multi-signer APKs entirely: we cannot safely pin a single cert
                // when multiple signers are present, and legitimate peers use one key.
                if (signing.hasMultipleSigners()) return null
                // apkContentsSigners returns only the CURRENT active signing cert(s).
                // signingCertificateHistory also returns rotated-away old certs, so an
                // attacker holding a compromised rotated key would still pass the check.
                signing.apkContentsSigners.firstOrNull()?.let { sha256Hex(it.toByteArray()) }
            } else {
                @Suppress("DEPRECATION")
                val sigs = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
                // Pre-API-28: the array may contain multiple entries for multi-signer APKs
                // or jarsigner legacy chains. Reject any APK with more than one signer to
                // prevent an attacker slipping an unrecognized cert into the array.
                if (sigs == null || sigs.size != 1) return null
                sha256Hex(sigs[0].toByteArray())
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02X".format(it) }
    }
}
