package com.eaglesakura.firearm.event

import android.os.Parcelable

/**
 * Any event(Error, Data Found, or such event) object.
 */
@Deprecated("https://github.com/eaglesakura/eventstream")
interface Event {
    val id: Any
}

/**
 *  can parcel event.
 */
@Deprecated("https://github.com/eaglesakura/eventstream")
interface ParcelableEvent : Event, Parcelable

@Deprecated("https://github.com/eaglesakura/eventstream")
typealias ParcerableEvent = ParcelableEvent
