package com.eaglesakura.firearm.event

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
class EventId(private val name: String) : Event {
    override val id: Any
        get() = name

    override fun toString(): String {
        return name
    }

    /**
     * This instance is only-one.
     */
    final override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    /**
     * This instance is only-one.
     */
    final override fun hashCode(): Int {
        return super.hashCode()
    }
}
