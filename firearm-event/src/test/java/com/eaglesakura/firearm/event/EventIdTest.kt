package com.eaglesakura.firearm.event

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eaglesakura.armyknife.android.junit4.extensions.compatibleTest
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventIdTest {

    @Test
    fun eventIdTest() = compatibleTest {
        val id0 = EventId("EVENT")
        val id1 = EventId("EVENT")
        assertEquals(id0.toString(), "EVENT")
        assertEquals(id1.toString(), "EVENT")
        assertTrue(id0 == id0)
        assertEquals(id0, id0)
        assertEquals(id0, id1)
    }
}
