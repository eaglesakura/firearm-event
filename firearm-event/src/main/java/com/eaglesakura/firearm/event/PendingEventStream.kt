package com.eaglesakura.firearm.event

import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.eaglesakura.armyknife.android.extensions.UIHandler
import com.eaglesakura.armyknife.android.extensions.assertUIThread
import com.eaglesakura.armyknife.android.extensions.forceActiveAlive
import com.eaglesakura.armyknife.android.extensions.postOrRun
import com.eaglesakura.armyknife.android.reactivex.with
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.subjects.PublishSubject
import kotlinx.android.parcel.Parcelize

/**
 * Event stream with suspend.
 */
class PendingEventStream {

    /**
     *  Stream for ViewModel.
     */
    constructor(
        savedStateKey: String,
        savedStateHandle: SavedStateHandle,
        validator: (event: ParcerableEvent) -> Boolean
    ) {
        this.validate = {
            require(it is ParcerableEvent)
            validator(it)
        }
        this.savedStateKey = savedStateKey
        this.savedStateHandle = savedStateHandle

        // restore data.
        UIHandler.postOrRun {
            pendingEventData.value =
                when (val saved = savedStateHandle.get<SavedPendingEvent>(savedStateKey)) {
                    null -> null
                    else -> PendingEvent(saved)
                }
        }
    }

    constructor(validator: (event: Event) -> Boolean) : super() {
        this.validate = validator
        this.savedStateKey = null
        this.savedStateHandle = null
    }

    private val validate: (event: Event) -> Boolean

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
    internal var subject: PublishSubject<Event>? = null

    @VisibleForTesting
    internal val pendingEventData = object : MutableLiveData<PendingEvent>() {
        override fun onActive() {
            val pending = this.value
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
    }

    /**
     *  Send or Pending next event.
     */
    fun next(event: Event) {
        UIHandler.post {
            nextNow(event)
        }
    }

    /**
     *  Send or Pending next event.
     */
    @UiThread
    fun nextNow(event: Event) {
        assertUIThread()

        require(validate(event)) {
            "Invalid event='$event'"
        }

        when {
            pendingEventData.hasActiveObservers() -> {
                subject!!.onNext(event)
            }
            pendingEventData.value == null -> {
                pendingEventData.value = PendingEvent(event)
            }
            else -> {
                pendingEventData.value = (pendingEventData.value!!).append(event)
            }
        }

        // save state.
        when {
            savedStateHandle != null && savedStateKey != null -> {
                savedStateHandle.set(savedStateKey, pendingEventData.value?.toParcelable())
            }
        }
    }

    /**
     *  Subscribe without Lifecycle.
     *  This function CAN'T close subject.
     */
    @CheckResult
    @UiThread
    fun observable(): Observable<Event> {
        assertUIThread()

        pendingEventData.observeForever { }
        return requireNotNull(subject) {
            "Invalid stream state"
        }
    }

    /**
     *  Get observable with LifecycleOwner.
     */
    @CheckResult
    @UiThread
    fun observable(owner: LifecycleOwner): Observable<Event> {
        assertUIThread()
        pendingEventData.forceActiveAlive(owner)
        return requireNotNull(subject) {
            "Invalid stream state"
        }
    }

    /**
     *  Subscribe util.
     */
    @UiThread
    fun subscribe(owner: LifecycleOwner, consumer: Consumer<Event>): Disposable {
        return observable(owner)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(consumer)
            .with(owner)
    }
}

internal data class PendingEvent internal constructor(
    val pendingEvents: List<Event>
) {

    internal constructor(event: Event) : this(listOf(event))

    internal constructor(saved: SavedPendingEvent) : this(saved.pendingEvents)

    internal fun append(event: Event): PendingEvent {
        val result = ArrayList(pendingEvents)
        result.add(event)
        return PendingEvent(result)
    }

    /**
     *  Convert to Saving Model.
     */
    internal fun toParcelable(): SavedPendingEvent {
        return SavedPendingEvent(
            this.pendingEvents.map { it as ParcerableEvent }
        )
    }
}

@Parcelize
internal data class SavedPendingEvent internal constructor(
    val pendingEvents: List<ParcerableEvent>
) : Parcelable