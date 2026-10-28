package com.divineit.convertor.domain.unicode

import java.text.Normalizer

/**
 * Unicode integrity analysis. The watchlist comes from real OCR failure
 * modes observed on Twi text: the open vowels ɛ and ɔ are repeatedly read
 * as plain e and o. The analyser flags such pairs for review; it never
 * silently "fixes" them, and it never treats a visually similar character
 * as automatically equivalent.
 *
 * Normalisation (NFC) is used only to *detect* that two tokens differ
 * solely by composed vs decomposed encoding. The stored text always
 * keeps its original code points.
 */
object UnicodeAnalyzer {

    enum class IssueKind {
        WATCHLIST_SUBSTITUTION,
        NORMALISATION_DIFFERENCE,
        REPLACEMENT_CHAR
    }

    /** Single-character substitutions seen in real OCR output. */
    private val watchlist = mapOf(
        'ɛ' to 'e',
        'ɔ' to 'o',
        'Ɛ' to 'E',
        'Ɔ' to 'O'
    )

    /** True when two tokens differ only by watchlist substitutions
     *  (same code-point length, every differing position a watchlist
     *  pair in either direction). */
    fun isWatchlistPair(a: String, b: String): Boolean {
        val aPoints = a.codePoints().toArray()
        val bPoints = b.codePoints().toArray()
        if (aPoints.size != bPoints.size) return false
        var anyWatchlist = false
        for (i in aPoints.indices) {
            val ac = aPoints[i]
            val bc = bPoints[i]
            if (ac == bc) continue
            val acChar = if (ac < 0x10000) ac.toChar() else null
            val bcChar = if (bc < 0x10000) bc.toChar() else null
            val forward = acChar != null && watchlist[acChar] == bcChar
            val backward = bcChar != null && watchlist[bcChar] == acChar
            if (!forward && !backward) return false
            anyWatchlist = true
        }
        return anyWatchlist
    }

    /** True when the tokens look identical after NFC normalisation but
     *  are stored differently (composed vs decomposed sequences). */
    fun normalisationDiffers(a: String, b: String): Boolean {
        if (a == b) return false
        return Normalizer.normalize(a, Normalizer.Form.NFC) ==
            Normalizer.normalize(b, Normalizer.Form.NFC)
    }

    /** True when the text contains U+FFFD, the replacement character
     *  that marks content the reader could not decode. */
    fun containsReplacementChar(text: String): Boolean =
        text.contains('\uFFFD')

    /** Classifies one differing token pair, or null when it is an
     *  ordinary substitution with no Unicode story. */
    fun classifyPair(a: String, b: String): IssueKind? {
        return when {
            isWatchlistPair(a, b) -> IssueKind.WATCHLIST_SUBSTITUTION
            normalisationDiffers(a, b) -> IssueKind.NORMALISATION_DIFFERENCE
            else -> null
        }
    }
}
