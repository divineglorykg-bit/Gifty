package com.divineit.convertor.domain.diff

/**
 * Myers O(ND) sequence alignment over token texts (or code-point strings
 * for character diffs). Deterministic, no network, no AI — exact
 * verification must never depend on a model.
 *
 * Deliberate scope: the engine runs Myers per paragraph pair, never on a
 * whole document at once, so a large book cannot blow up time or memory.
 * A paragraph pair with either side over 2000 tokens falls back to
 * reporting the whole pair as substituted, rather than hanging.
 */
object MyersDiff {

    sealed class Op {
        data class Equal(val origIndex: Int, val prodIndex: Int) : Op()
        data class Delete(val origIndex: Int) : Op()
        data class Insert(val prodIndex: Int) : Op()
    }

    fun diff(a: List<String>, b: List<String>): List<Op> {
        val n = a.size
        val m = b.size
        if (n == 0) return b.indices.map { Op.Insert(it) }
        if (m == 0) return a.indices.map { Op.Delete(it) }
        if (maxOf(n, m) > 2000) {
            // Safety fallback: report the pair as substituted wholesale.
            return a.indices.map { Op.Delete(it) } +
                b.indices.map { Op.Insert(it) }
        }
        val max = n + m
        val offset = max
        var v = IntArray(2 * max + 1)
        val trace = mutableListOf<IntArray>()
        var foundD = -1
        outer@ for (d in 0..max) {
            trace.add(v.copyOf())
            for (k in -d..d step 2) {
                var x = if (k == -d ||
                    (k != d && v[offset + k - 1] < v[offset + k + 1])
                ) {
                    v[offset + k + 1]
                } else {
                    v[offset + k - 1] + 1
                }
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) {
                    x++
                    y++
                }
                v[offset + k] = x
                if (x >= n && y >= m) {
                    foundD = d
                    break@outer
                }
            }
        }
        val ops = mutableListOf<Op>()
        var x = n
        var y = m
        for (d in foundD downTo 1) {
            val vv = trace[d]
            val k = x - y
            val prevK = if (k == -d ||
                (k != d && vv[offset + k - 1] < vv[offset + k + 1])
            ) {
                k + 1
            } else {
                k - 1
            }
            val prevX = vv[offset + prevK]
            val prevY = prevX - prevK
            while (x > prevX && y > prevY) {
                ops.add(Op.Equal(x - 1, y - 1))
                x--
                y--
            }
            if (x == prevX) {
                ops.add(Op.Insert(y - 1))
                y--
            } else {
                ops.add(Op.Delete(x - 1))
                x--
            }
        }
        while (x > 0 && y > 0) {
            ops.add(Op.Equal(x - 1, y - 1))
            x--
            y--
        }
        return ops.reversed()
    }
}
