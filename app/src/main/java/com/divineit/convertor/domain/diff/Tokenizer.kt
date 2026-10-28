package com.divineit.convertor.domain.diff

/** Token kinds. NUMBER is a token of digits only, so a changed figure is
 *  classified as a number change rather than a word substitution. */
enum class TokenKind { WORD, PUNCT, NUMBER }

/** A token with its character offsets in the source text (`start`
 *  inclusive, `end` exclusive). Offsets let the Phase 4 correction
 *  engine splice a replacement into the exact position instead of
 *  guessing. */
data class Token(
    val text: String,
    val kind: TokenKind,
    val start: Int = 0,
    val end: Int = 0
)

/**
 * Unicode-aware tokeniser. It walks code points, never UTF-16 units, so
 * characters outside the BMP and combining sequences are never split.
 * Words are runs of letters, digits and marks; an apostrophe joins a word
 * only when it sits between two word characters ("don't", "l'amour");
 * every other punctuation mark is its own token, so "word," never hides
 * a punctuation change inside a word token. Whitespace is not a token:
 * spacing differences are deliberately not reported in Phase 3 (the
 * extractor already normalises whitespace runs).
 */
object Tokenizer {

    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val word = StringBuilder()
        var wordStart = -1
        var i = 0
        fun flushWord(endExclusive: Int) {
            if (word.isNotEmpty()) {
                val w = word.toString()
                val kind = if (w.all { Character.isDigit(it) }) {
                    TokenKind.NUMBER
                } else {
                    TokenKind.WORD
                }
                tokens.add(Token(w, kind, wordStart, endExclusive))
                word.clear()
                wordStart = -1
            }
        }
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            when {
                isWordChar(cp) -> {
                    if (word.isEmpty()) wordStart = i
                    word.appendCodePoint(cp)
                }
                cp == '\''.code || cp == 0x2019 -> {
                    // Apostrophe joins only between two word characters.
                    val nextCp = if (i + charCount < text.length) {
                        text.codePointAt(i + charCount)
                    } else {
                        -1
                    }
                    if (word.isNotEmpty() && nextCp != -1 && isWordChar(nextCp)) {
                        word.appendCodePoint(cp)
                    } else {
                        flushWord(i)
                        tokens.add(
                            Token(
                                String(Character.toChars(cp)),
                                TokenKind.PUNCT, i, i + charCount
                            )
                        )
                    }
                }
                Character.isWhitespace(cp) -> flushWord(i)
                else -> {
                    flushWord(i)
                    tokens.add(
                        Token(
                            String(Character.toChars(cp)),
                            TokenKind.PUNCT, i, i + charCount
                        )
                    )
                }
            }
            i += charCount
        }
        flushWord(text.length)
        return tokens
    }

    private fun isWordChar(cp: Int): Boolean {
        // getType returns Int in Kotlin; the category constants are Byte.
        val type = Character.getType(cp).toByte()
        return when (type) {
            Character.UPPERCASE_LETTER,
            Character.LOWERCASE_LETTER,
            Character.TITLECASE_LETTER,
            Character.MODIFIER_LETTER,
            Character.OTHER_LETTER,
            Character.DECIMAL_DIGIT_NUMBER,
            Character.LETTER_NUMBER,
            Character.OTHER_NUMBER,
            Character.NON_SPACING_MARK,
            Character.COMBINING_SPACING_MARK,
            Character.ENCLOSING_MARK -> true
            else -> false
        }
    }
}
