package com.divineit.convertor.domain.diff

/** Character-level diff for a single substituted word, computed on code
 *  points. This is what turns "sɛ" → "se" into the precise evidence
 *  "character 2: 'ɛ' → 'e'" instead of a vague "word changed". */
object CharDiff {

    data class CharChange(
        val position: Int,
        val original: String?,
        val product: String?
    )

    fun changes(a: String, b: String): List<CharChange> {
        val aPoints = a.codePoints().toArray()
            .map { String(Character.toChars(it)) }
        val bPoints = b.codePoints().toArray()
            .map { String(Character.toChars(it)) }
        val ops = MyersDiff.diff(aPoints, bPoints)
        val changes = mutableListOf<CharChange>()
        var deletes = mutableListOf<String>()
        var inserts = mutableListOf<String>()
        var position = 0
        fun flush() {
            if (deletes.isNotEmpty() || inserts.isNotEmpty()) {
                changes.add(
                    CharChange(
                        position,
                        deletes.joinToString("").ifEmpty { null },
                        inserts.joinToString("").ifEmpty { null }
                    )
                )
                deletes = mutableListOf()
                inserts = mutableListOf()
            }
        }
        for (op in ops) {
            when (op) {
                is MyersDiff.Op.Equal -> {
                    flush()
                    position++
                }
                is MyersDiff.Op.Delete -> deletes.add(aPoints[op.origIndex])
                is MyersDiff.Op.Insert -> inserts.add(bPoints[op.prodIndex])
            }
        }
        flush()
        return changes
    }

    /** One-line evidence, e.g. "'ɛ' → 'e'". Empty when the words are
     *  identical (which cannot happen for a reported substitution). */
    fun summarize(a: String, b: String): String {
        return changes(a, b).joinToString(", ") { change ->
            "'${change.original ?: ""}' → '${change.product ?: ""}'"
        }
    }
}
