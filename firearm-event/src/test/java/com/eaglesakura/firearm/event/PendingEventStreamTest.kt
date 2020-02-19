package com.eaglesakura.firearm.event

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eaglesakura.armyknife.android.ApplicationRuntime
import com.eaglesakura.armyknife.android.junit4.extensions.compatibleBlockingTest
import com.eaglesakura.armyknife.android.junit4.extensions.makeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingEventStreamTest {

    @Test
    fun newInstance() = compatibleBlockingTest {
        val stream = PendingEventStream { true }

        assertEquals(PendingEventStream.StreamMode.Auto, stream.mode)
        assertNull(stream.savedStateHandle)
        assertNull(stream.savedStateKey)
        assertTrue(stream.pendingEventList.isEmpty())
    }

    @Test
    fun active_inactive() = compatibleBlockingTest {
        val activity = makeActivity()
        val stream = PendingEventStream(activity) { true }

        withContext(Dispatchers.Main) {
            val channel = stream.testChannel()
            assertTrue(stream.isActive)

            if (ApplicationRuntime.runIn(ApplicationRuntime.RUNTIME_INSTRUMENTATION)) {
                activity.finish()
                delay(1000)

                assertFalse(stream.isActive)
            }
        }
    }

    @Test
    fun pending_receive() = compatibleBlockingTest {
        val stream = PendingEventStream { true }
        stream.next(PENDING_EVENT_GET)
        stream.next(PENDING_EVENT_SET)
        stream.next(PENDING_EVENT_UPDATE)

        withContext(Dispatchers.Main) {
            yield()
            assertEquals(3, stream.pendingEventList.size)
            assertEquals(PENDING_EVENT_GET, stream.pendingEventList[0])
            assertEquals(PENDING_EVENT_SET, stream.pendingEventList[1])
            assertEquals(PENDING_EVENT_UPDATE, stream.pendingEventList[2])
        }

        val activity = makeActivity()
        withContext(Dispatchers.Main) {
            val channel = stream.testChannel(Dispatchers.Main)
            delay(1000)
            assertEquals(0, stream.pendingEventList.size)
            assertEquals(PENDING_EVENT_GET, channel.receive())
            assertEquals(PENDING_EVENT_SET, channel.receive())
            assertEquals(PENDING_EVENT_UPDATE, channel.receive())
        }
    }

    @Test
    fun receive() = compatibleBlockingTest {
        val activity = makeActivity()
        val stream = PendingEventStream(activity) { true }
        withContext(Dispatchers.Main) {
            val channel = stream.testChannel(Dispatchers.Main)

            stream.next(PENDING_EVENT_GET)
            delay(1000)
            stream.pauseStream()

            stream.next(PENDING_EVENT_SET)
            stream.next(PENDING_EVENT_UPDATE)

            delay(1000)
            assertFalse(channel.isEmpty)
            assertEquals(PENDING_EVENT_GET, channel.receive())
            delay(1000)
            assertTrue(channel.isEmpty)

            stream.resumeStream()
            assertEquals(PENDING_EVENT_SET, channel.receive())
            assertEquals(PENDING_EVENT_UPDATE, channel.receive())
        }
    }

    @Test
    fun receive_forever() = compatibleBlockingTest {
        val stream = PendingEventStream { true }
        val channel = stream.testChannel(Dispatchers.Main)
        stream.next(PENDING_EVENT_GET)
        stream.next(PENDING_EVENT_SET)
        stream.next(PENDING_EVENT_UPDATE)

        assertEquals(PENDING_EVENT_GET, channel.receive())
        assertEquals(PENDING_EVENT_SET, channel.receive())
        assertEquals(PENDING_EVENT_UPDATE, channel.receive())
    }

    companion object {
        val PENDING_EVENT_GET = EventId("PENDING_EVENT_GET")
        val PENDING_EVENT_SET = EventId("PENDING_EVENT_SET")
        val PENDING_EVENT_UPDATE = EventId("PENDING_EVENT_UPDATE")
    }
}
