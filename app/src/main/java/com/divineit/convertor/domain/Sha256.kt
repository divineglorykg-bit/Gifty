package com.divineit.convertor.domain

import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/** SHA-256 helpers (integrity checks only — a matching hash never
 *  substitutes for content comparison across different formats). */
object Sha256 {

    fun hash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).toHex()
    }

    fun hashStream(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, digest).use { stream ->
            val buffer = ByteArray(8192)
            while (stream.read(buffer) != -1) {
                // Reading drives the digest; no buffering of content.
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
