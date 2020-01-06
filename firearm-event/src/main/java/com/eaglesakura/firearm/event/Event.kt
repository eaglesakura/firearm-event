package com.eaglesakura.firearm.event

import android.os.Parcelable

/**
 * Any event(Error, Data Found, or such event) object.
 */
interface Event {
    val id: Any
}

/**
 *  can parcel event.
 */
interface ParcerableEvent : Event, Parcelable