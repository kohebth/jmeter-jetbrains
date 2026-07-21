package com.github.kohebth.jmeterviewer.editor

internal data class TextReplacement(
    val offset: Int,
    val oldLength: Int,
    val newText: String,
) {
    companion object {
        fun between(oldText: CharSequence, newText: CharSequence): TextReplacement? {
            if (oldText.contentEquals(newText)) {
                return null
            }

            val commonLimit = minOf(oldText.length, newText.length)
            var prefixLength = 0
            while (
                prefixLength < commonLimit &&
                oldText[prefixLength] == newText[prefixLength]
            ) {
                prefixLength++
            }

            var oldSuffix = oldText.length
            var newSuffix = newText.length
            while (
                oldSuffix > prefixLength &&
                newSuffix > prefixLength &&
                oldText[oldSuffix - 1] == newText[newSuffix - 1]
            ) {
                oldSuffix--
                newSuffix--
            }

            return TextReplacement(
                offset = prefixLength,
                oldLength = oldSuffix - prefixLength,
                newText = newText.subSequence(prefixLength, newSuffix).toString(),
            )
        }
    }
}
