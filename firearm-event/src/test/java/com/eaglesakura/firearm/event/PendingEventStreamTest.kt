package com.eaglesakura.firearm.event

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eaglesakura.armyknife.android.ApplicationRuntime
import com.eaglesakura.armyknife.android.junit4.extensions.compatibleBlockingTest
import com.eaglesakura.armyknife.android.junit4.extensions.makeActivity
import com.eaglesakura.armyknife.android.reactivex.toChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        assertNull(stream.pendingEventData.value)
    }

    @Test
    fun active_inactive() = compatibleBlockingTest {
        val stream = PendingEventStream { true }
        val activity = makeActivity()

        withContext(Dispatchers.Main) {
            val observable = stream.observable(activity)
            assertNotNull(observable)
            assertNotNull(stream.subject)

            if (ApplicationRuntime.runIn(ApplicationRuntime.RUNTIME_INSTRUMENTATION)) {
                activity.finish()
                delay(1000)

                // closed stream
                assertNull(stream.subject)
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
            assertNotNull(stream.pendingEventData.value)
            assertEquals(3, stream.pendingEventData.value!!.pendingEvents.size)
            assertEquals(PENDING_EVENT_GET, stream.pendingEventData.value!!.pendingEvents[0])
            assertEquals(PENDING_EVENT_SET, stream.pendingEventData.value!!.pendingEvents[1])
            assertEquals(PENDING_EVENT_UPDATE, stream.pendingEventData.value!!.pendingEvents[2])
        }

        val activity = makeActivity()
        withContext(Dispatchers.Main) {
            val channel = stream.observable(activity).toChannel(Dispatchers.Main)

            assertNull(stream.pendingEventData.value)
            assertEquals(PENDING_EVENT_GET, channel.receive())
            assertEquals(PENDING_EVENT_SET, channel.receive())
            assertEquals(PENDING_EVENT_UPDATE, channel.receive())
        }
    }

    @Test
    fun receive() = compatibleBlockingTest {
        val stream = PendingEventStream { true }
        val activity = makeActivity()
        withContext(Dispatchers.Main) {
            val channel = stream.testChannel(Dispatchers.Main)

            stream.next(PENDING_EVENT_GET)
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
        val channel = withContext(Dispatchers.Main) {
            stream.observable().toChannel(Dispatchers.Main)
        }
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
