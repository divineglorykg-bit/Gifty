package com.divineit.convertor.domain.alignment

/**
 * Paragraph alignment between the original and product documents.
 *
 * Strategy: exact-match anchors from a longest-common-subsequence pass,
 * then the gaps between anchors are paired positionally when their
 * counts match, or greedily by token overlap (Jaccard ≥ 0.3) when they
 * do not. Anything left over is reported as missing (original-only) or
 * extra (product-only). Paragraph order is preserved throughout.
 *
 * Guard: if orig.size × prod.size exceeds 4,000,000 the LCS pass is
 * skipped and the whole document is treated as one gap, so a very large
 * book cannot exhaust memory on the DP table.
 */
object ParagraphAligner {

    data class Pairing(val origIndex: Int?, val prodIndex: Int?)

    fun align(orig: List<String>, prod: List<String>): List<Pairing> {
        val anchors = lcsAnchors(orig, prod)
        val result = mutableListOf<Pairing>()
        var prevO = -1
        var prevP = -1
        fun handleSegment(oFrom: Int, oTo: Int, pFrom: Int, pTo: Int) {
            val oSeg = (oFrom until oTo).toList()
            val pSeg = (pFrom until pTo).toList()
            if (oSeg.isEmpty()) {
                pSeg.forEach { result.add(Pairing(null, it)) }
                return
            }
            if (pSeg.isEmpty()) {
                oSeg.forEach { result.add(Pairing(it, null)) }
                return
            }
            if (oSeg.size == pSeg.size) {
                oSeg.zip(pSeg).forEach { (o, p) -> result.add(Pairing(o, p)) }
                return
            }
            val usedP = mutableSetOf<Int>()
            for (o in oSeg) {
                var best: Int? = null
                var bestScore = 0.3
                for (p in pSeg) {
                    if (p in usedP) continue
                    val score = jaccard(orig[o], prod[p])
                    if (score > bestScore) {
                        bestScore = score
                        best = p
                    }
                }
                if (best != null) {
                    usedP.add(best)
                    result.add(Pairing(o, best))
                } else {
                    result.add(Pairing(o, null))
                }
            }
            pSeg.filter { it !in usedP }.forEach { result.add(Pairing(null, it)) }
        }
        for ((anchorO, anchorP) in anchors) {
            handleSegment(prevO + 1, anchorO, prevP + 1, anchorP)
            result.add(Pairing(anchorO, anchorP))
            prevO = anchorO
            prevP = anchorP
        }
        handleSegment(prevO + 1, orig.size, prevP + 1, prod.size)
        return result
    }

    private fun lcsAnchors(orig: List<String>, prod: List<String>): List<Pair<Int, Int>> {
        val n = orig.size
        val m = prod.size
        if (n == 0 || m == 0) return emptyList()
        if (n.toLong() * m.toLong() > 4_000_000L) return emptyList()
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) {
            for (j in 1..m) {
                dp[i][j] = if (orig[i - 1] == prod[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        val anchors = mutableListOf<Pair<Int, Int>>()
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            when {
                orig[i - 1] == prod[j - 1] -> {
                    anchors.add(Pair(i - 1, j - 1))
                    i--
                    j--
                }
                dp[i - 1][j] >= dp[i][j - 1] -> i--
                else -> j--
            }
        }
        return anchors.reversed()
    }

    private fun jaccard(a: String, b: String): Double {
        val aTokens = a.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        val bTokens = b.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0.0
        val intersection = aTokens.intersect(bTokens).size.toDouble()
        val union = aTokens.union(bTokens).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }
}
