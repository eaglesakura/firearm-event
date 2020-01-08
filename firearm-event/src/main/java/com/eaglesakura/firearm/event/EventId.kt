package com.eaglesakura.firearm.event

import kotlinx.android.parcel.Parcelize

/**
 * "EventId" is stream-item for RxStream<Event>(alias to EventStream).
 *
 *  EventId will validated before than send to the observable.
 *  Objects of EventStream.subscribe() are will be received only verified events.
 *
 * e.g.)
 * object Foo {
 *      val event = EventStream<Event> { event ->
 *          when(event) {
 *              EVENT_EXAMPLE -> true   // exist event.
 *              else -> false   // This event has been not supported!!
 *          }
 *      }
 *
 *      fun onSomeEvent() {
 *          event.next(EVENT_EXAMPLE) // ok
 *      }
 *
 *      fun onSomeEvent_error() {
 *          event.next(EventId("NOT SUPPORTED")) // error, throw exception from RxStream.
 *      }
 *
 *      companion object {
 *          val EVENT_EXAMPLE = EventId("EVENT_EXAMPLE")
 *      }
 * }
 */
@Parcelize
class EventId(private val name: String) : ParcelableEvent {
    override val id: Any
        get() = name

    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EventId

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
