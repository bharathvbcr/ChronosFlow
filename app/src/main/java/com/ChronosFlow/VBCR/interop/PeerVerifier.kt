package com.ChronosFlow.VBCR.interop

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
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

        val pinned = peer.certSha256.map(::normalize).toSet()
        if (pinned.isEmpty()) {
            Log.w(
                TAG,
                "Trusting $callingPackage by package name only (no cert pinned — insecure). " +
                    "Pin this SHA-256 in InteropContract.TRUSTED_PEERS: $actual",
            )
            return
        }
        if (normalize(actual) !in pinned) {
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
        val pinned = peer.certSha256.map(::normalize).toSet()
        return pinned.isEmpty() || normalize(actual) in pinned
    }

    /** Canonicalize a SHA-256 hash so colons, whitespace and case don't affect comparison. */
    private fun normalize(hash: String): String =
        hash.filterNot { it == ':' || it.isWhitespace() }.uppercase()

    private fun signingSha256(context: Context, pkg: String): String? {
        val pm = context.packageManager
        return try {
            val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signing = info.signingInfo ?: return null
                if (signing.hasMultipleSigners()) signing.apkContentsSigners
                else signing.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            }
            signatures?.firstOrNull()?.let { sha256Hex(it.toByteArray()) }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02X".format(it) }
    }
}
