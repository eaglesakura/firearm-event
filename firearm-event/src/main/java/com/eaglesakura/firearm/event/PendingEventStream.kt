package com.eaglesakura.firearm.event

import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import com.eaglesakura.armyknife.android.extensions.UIHandler
import com.eaglesakura.armyknife.android.extensions.assertUIThread
import com.eaglesakura.armyknife.android.extensions.forceActiveAlive
import com.eaglesakura.armyknife.android.extensions.postOrRun
import com.eaglesakura.armyknife.android.extensions.registerFinalizer
import com.eaglesakura.armyknife.android.reactivex.toChannel
import com.eaglesakura.armyknife.android.reactivex.with
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.subjects.PublishSubject
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import java.io.Closeable

/**
 * Event stream with suspend.
 */
class PendingEventStream : Closeable {

    /**
     *  Stream for ViewModel.
     */
    constructor(
        savedStateKey: String,
        savedStateHandle: SavedStateHandle,
        validator: (event: ParcelableEvent) -> Boolean
    ) {
        this.validate = {
            require(it is ParcelableEvent)
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

    constructor(lifecycleOwner: LifecycleOwner, validator: (event: Event) -> Boolean) : this(
            validator
    ) {
        autoClose(lifecycleOwner)
    }

    /**
     * Auto close this resource.
     */
    fun autoClose(lifecycleOwner: LifecycleOwner): PendingEventStream {
        lifecycleOwner.registerFinalizer {
            close()
        }
        return this
    }

    @VisibleForTesting
    internal var mode: StreamMode = StreamMode.Auto

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
    internal val subject: PublishSubject<Event> = PublishSubject.create()

    @VisibleForTesting
    internal val pendingEventData = object : MutableLiveData<PendingEvent>() {
        override fun onActive() {
            resumeStreamImpl()
        }

        override fun onInactive() {
            pauseStreamImpl()
        }
    }

    private fun pauseStreamImpl() {
    }

    private fun resumeStreamImpl() {
        val pending = pendingEventData.value
        pendingEventData.value = null
        pending?.also {
            for (event in it.pendingEvents) {
                next(event)
            }
        }
    }

    /**
     * check this stream is active.
     */
    val isActive: Boolean
        get() = when {
            mode == StreamMode.Pause -> false
            !pendingEventData.hasActiveObservers() -> false
            else -> true
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
            mode == StreamMode.Auto && pendingEventData.hasActiveObservers() -> {
                subject.onNext(event)
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
     * Pause this stream.
     */
    @UiThread
    fun pauseStream() {
        assertUIThread()
        this.mode = StreamMode.Pause
        pauseStreamImpl()
    }

    /**
     * Resume this stream.
     */
    @UiThread
    fun resumeStream() {
        assertUIThread()
        UIHandler.post {
            this.mode = StreamMode.Auto
            resumeStreamImpl()
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
     *  Subscribe util.
     */
    @UiThread
    fun subscribe(owner: LifecycleOwner, onNext: (event: Event) -> Unit): Disposable {
        return this.subscribe(owner, Consumer { onNext(it) })
    }

    /**
     * Subscribe util.
     */
    @UiThread
    fun subscribeWhenCreated(owner: LifecycleOwner, onNext: (event: Event) -> Unit): Disposable {
        return subscribe(owner) {
            owner.lifecycleScope.launchWhenCreated { onNext(it) }
        }
    }

    /**
     * Subscribe util.
     */
    @UiThread
    fun subscribeWhenStarted(owner: LifecycleOwner, onNext: (event: Event) -> Unit): Disposable {
        return subscribe(owner) {
            owner.lifecycleScope.launchWhenStarted { onNext(it) }
        }
    }

    /**
     * Subscribe util.
     */
    @UiThread
    fun subscribeWhenResumed(owner: LifecycleOwner, onNext: (event: Event) -> Unit): Disposable {
        return subscribe(owner) {
            owner.lifecycleScope.launchWhenResumed { onNext(it) }
        }
    }

    override fun close() {
        subject.onComplete()
    }

    /**
     *  Subscribe util.
     */
    @UiThread
    fun subscribe(owner: LifecycleOwner, onNext: Consumer<Event>): Disposable {
        return observable(owner)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onNext)
                .with(owner)
    }

    /**
     * Make channel for Testing.
     */
    @UiThread
    fun testChannel(dispatcher: CoroutineDispatcher = Dispatchers.Main): Channel<Event> {
        return observable().toChannel(dispatcher)
    }

    /**
     * Stream mode.
     */
    internal enum class StreamMode {
        /**
         * Auto pending, Auto resume.
         */
        Auto,

        /**
         * Force pending.
         */
        Pause,
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
                this.pendingEvents.map { it as ParcelableEvent }
        )
    }
}

@Parcelize
internal data class SavedPendingEvent internal constructor(
    val pendingEvents: List<ParcelableEvent>
) : Parcelable