package com.divineit.convertor

import com.divineit.convertor.domain.progress.WorkProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Progress percentages are shown in the UI for long operations,
 *  so the arithmetic must be exact and never exceed 100. */
class WorkProgressTest {

    @Test
    fun percentIsNullWhenNotCountable() {
        assertNull(WorkProgress("Writing file…").percent)
        assertNull(WorkProgress("Reading", done = 3, total = null).percent)
        assertNull(WorkProgress("Reading", done = null, total = 10).percent)
        assertNull(WorkProgress("Reading", done = 0, total = 0).percent)
    }

    @Test
    fun percentRoundsDownAndClamps() {
        assertEquals(0, WorkProgress("R", 0, 40).percent)
        assertEquals(30, WorkProgress("R", 12, 40).percent)
        assertEquals(33, WorkProgress("R", 1, 3).percent)
        assertEquals(100, WorkProgress("R", 40, 40).percent)
        assertEquals(100, WorkProgress("R", 45, 40).percent)
    }

    @Test
    fun displayTextAppendsPercentOnlyWhenKnown() {
        assertEquals("Reading…", WorkProgress("Reading…").displayText)
        assertEquals(
            "Reading — 25%",
            WorkProgress("Reading", 1, 4).displayText
        )
    }
}
