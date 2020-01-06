package com.eaglesakura.firearm.event

import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import com.eaglesakura.armyknife.android.extensions.UIHandler
import com.eaglesakura.armyknife.android.extensions.assertUIThread
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.parcel.Parcelize

/**
 * Event stream with suspend.
 */
class PendingEventStream : LiveData<Parcelable> {

    /**
     *  Stream for ViewModel.
     */
    constructor(
        savedStateKey: String,
        savedStateHandle: SavedStateHandle,
        validator: (event: ParcerableEvent) -> Boolean
    ) : super() {
        this.validate = validator
        this.savedStateKey = savedStateKey
        this.savedStateHandle = savedStateHandle

        // restore data.
        this.value = savedStateHandle.get(savedStateKey)
    }

    constructor(validator: (event: ParcerableEvent) -> Boolean) : super() {
        this.validate = validator
        this.savedStateKey = null
        this.savedStateHandle = null
    }

    private val validate: (event: ParcerableEvent) -> Boolean

    /**
     *  for ViewModel save.
     */
    @VisibleForTesting
    internal val savedStateKey: String?

    /**
     *  for ViewModel save.
     */
    @VisibleForTesting
    internal val savedStateHandle: SavedStateHandle?

    @VisibleForTesting
    internal var subject: PublishSubject<ParcerableEvent>? = null

    /**
     *  Pending object ref.
     */
    @VisibleForTesting
    internal val pending: PendingEvent?
        get() {
            val obj = this.value ?: return null
            require(obj is PendingEvent) {
                "Invalid type(${obj.javaClass.simpleName})"
            }
            return obj
        }

    override fun onActive() {
        val pending = this.pending
        this.value = null
        subject = PublishSubject.create()
        pending?.also {
            for (event in it.pendingEvents) {
                next(event)
            }
        }
    }

    override fun onInactive() {
        subject?.onComplete()
        subject = null
    }

    /**
     *  Send or Pending next event.
     */
    fun next(event: ParcerableEvent) {
        UIHandler.post {
            nextNow(event)
        }
    }

    /**
     *  Send or Pending next event.
     */
    @UiThread
    fun nextNow(event: ParcerableEvent) {
        assertUIThread()

        require(validate(event)) {
            "Invalid event='$event'"
        }

        when {
            this.hasActiveObservers() -> {
                subject!!.onNext(event)
            }
            this.value == null -> {
                this.value = PendingEvent(event)
            }
            else -> {
                this.value = (this.pending!!).append(event)
            }
        }
    }

    override fun setValue(value: Parcelable?) {
        if (value != null) {
            require(value is PendingEvent) {
                "Invalid type(${value.javaClass.simpleName})"
            }
        }

        // save state.
        when {
            savedStateHandle != null && savedStateKey != null -> {
                savedStateHandle.set(savedStateKey, value)
            }
        }

        super.setValue(value)
    }

    /**
     *  Subscribe without Lifecycle.
     *  This function CAN'T close subject.
     */
    @CheckResult
    @UiThread
    fun observable(): Observable<ParcerableEvent> {
        assertUIThread()

        this.observeForever { }
        return requireNotNull(subject) {
            "Invalid stream state"
        }
    }

    /**
     *  Subscribe
     */
    @CheckResult
    @UiThread
    fun observable(owner: LifecycleOwner): Observable<ParcerableEvent> {
        assertUIThread()
        this.observe(owner, Observer { })
        return requireNotNull(subject) {
            "Invalid stream state"
        }
    }
}

@Parcelize
internal data class PendingEvent internal constructor(
    internal val pendingEvents: List<ParcerableEvent>
) : Parcelable {

    internal constructor(event: ParcerableEvent) : this(listOf(event))

    internal fun append(event: ParcerableEvent): PendingEvent {
        val result = ArrayList(pendingEvents)
        result.add(event)
        return PendingEvent(result)
    }
}