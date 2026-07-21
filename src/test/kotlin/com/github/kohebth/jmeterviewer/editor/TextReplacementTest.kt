package com.github.kohebth.jmeterviewer.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TextReplacementTest {
    @Test
    fun replacesOnlyTheChangedMiddleOfTheDocument() {
        assertEquals(
            TextReplacement(offset = 6, oldLength = 5, newText = "JMeter"),
            TextReplacement.between("hello world!", "hello JMeter!"),
        )
    }

    @Test
    fun handlesInsertionsAndDeletions() {
        assertEquals(
            TextReplacement(offset = 3, oldLength = 0, newText = "XYZ"),
            TextReplacement.between("abcdef", "abcXYZdef"),
        )
        assertEquals(
            TextReplacement(offset = 2, oldLength = 3, newText = ""),
            TextReplacement.between("abcdef", "abf"),
        )
    }

    @Test
    fun returnsNoEditForIdenticalText() {
        assertNull(TextReplacement.between("same", "same"))
    }
}
