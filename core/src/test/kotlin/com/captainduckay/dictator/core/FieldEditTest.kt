package com.captainduckay.dictator.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldEditTest {
    @Test fun unknownSelectionTypesAtTheEnd() {
        val field = FieldEdit.snapshot("Hello", -1, -1)
        assertEquals(FieldSnapshot("Hello there", 11, 11), FieldEdit.insert(field, " there"))
    }

    @Test fun insertReplacesASelection() {
        val field = FieldEdit.snapshot("ab cd", 3, 5)
        assertEquals(FieldSnapshot("ab xy", 5, 5), FieldEdit.insert(field, "xy"))
    }

    @Test fun reversedSelectionIsOrdered() {
        val field = FieldEdit.snapshot("abcd", 3, 1)
        assertEquals(FieldSnapshot("aXYd", 3, 3), FieldEdit.insert(field, "XY"))
    }

    @Test fun deleteStopsAtGraphemeBoundaries() {
        val family = "👩‍👩‍👧‍👦"
        val field = FieldEdit.snapshot("x${family}y", 1 + family.length, 1 + family.length)
        assertEquals(FieldSnapshot("xy", 1, 1), FieldEdit.deleteBeforeCursor(field, 1))
    }

    @Test fun deleteDoesNotRunPastTheStart() {
        val field = FieldEdit.snapshot("Hi", 2, 2)
        assertEquals(FieldSnapshot("", 0, 0), FieldEdit.deleteBeforeCursor(field, 10))
    }

    @Test fun precedingIsTheTailBeforeTheCaret() {
        val field = FieldEdit.snapshot("abcdefghij", 8, 8)
        assertEquals("defgh", FieldEdit.preceding(field, 5))
        assertEquals("abcdefgh", FieldEdit.preceding(field, 100))
    }
}
