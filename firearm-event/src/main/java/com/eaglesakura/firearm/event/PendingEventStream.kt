package com.eaglesakura.firearm.event

import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import com.eaglesakura.armyknife.android.extensions.UIHandler
import com.eaglesakura.armyknife.android.extensions.assertUIThread
import com.eaglesakura.armyknife.android.extensions.postOrRun
import com.eaglesakura.armyknife.android.extensions.registerFinalizer
import com.eaglesakura.armyknife.android.reactivex.toChannel
import com.eaglesakura.armyknife.android.reactivex.with
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
        _pendingEventList = when (val saved = savedStateHandle.get<Array<ParcelableEvent>>(savedStateKey)) {
            null -> emptyList()
            else -> saved.toList()
        }
        _mode = savedStateHandle.get<StreamMode>("$savedStateKey@mode") ?: StreamMode.Auto
    }

    /**
     *  Stream for ViewModel.
     */
    constructor(
            lifecycleOwner: LifecycleOwner,
            savedStateKey: String,
            savedStateHandle: SavedStateHandle,
            validator: (event: ParcelableEvent) -> Boolean
    ) : this(savedStateKey, savedStateHandle, validator) {
        autoClose(lifecycleOwner)
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

    private val lock = ReentrantLock()

    /**
     * Auto close this resource.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun autoClose(lifecycleOwner: LifecycleOwner): PendingEventStream {
        lifecycleOwner.registerFinalizer {
            close()
        }
        return this
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
    internal val subject: PublishSubject<Event> = PublishSubject.create()

    private var _pendingEventList: List<Event> = listOf()

    private var _mode: StreamMode = StreamMode.Auto

    @VisibleForTesting
    internal var mode: StreamMode
        get() = _mode
        set(value) {
            if (savedStateHandle != null && savedStateKey != null) {
                savedStateHandle.set("$savedStateKey@mode", value)
            }
            _mode = value
        }

    @VisibleForTesting
    internal var pendingEventList: List<Event>
        get() = _pendingEventList
        set(value) {
            if (savedStateHandle != null && savedStateKey != null) {
                savedStateHandle.set(savedStateKey, value.filterIsInstance<ParcelableEvent>().toTypedArray())
            }
            _pendingEventList = value
        }

    /**
     * check this stream is active.
     */
    val isActive: Boolean
        get() = when {
            mode == StreamMode.Pause -> false
            !subject.hasObservers() -> false
            else -> true
        }

    /**
     * remove Event object on returns true from lambda.
     *
     * e.g.)
     * val event: PendingEventStream = ...
     * event.removeIf {
     *      it is ExampleEvent // remove "ExampleEvent" instance from pending list.
     * }
     */
    @UiThread
    fun removeIf(block: (event: Event) -> Boolean) = lock.withLock {
        assertUIThread()
        this.pendingEventList = this.pendingEventList.filter { !block(it) }
    }

    /**
     * Interrupt this event.
     */
    fun interrupt(event: Event) {
        pushFront(event)
        UIHandler.post {
            broadcast()
        }
    }

    /**
     * Interrupt and force broadcast this event.
     */
    @UiThread
    fun interruptNow(event: Event) {
        assertUIThread()

        pushFront(event)
        broadcast()
    }

    /**
     *  Send or Pending next event.
     */
    fun next(event: Event) {
        pushBack(event)
        UIHandler.post {
            broadcast()
        }
    }

    /**
     *  Send or Pending next event.
     */
    @UiThread
    fun nextNow(event: Event) {
        assertUIThread()

        pushBack(event)
        broadcast()
    }

    @AnyThread
    private fun pushFront(event: Event) = lock.withLock {
        require(validate(event)) {
            "Invalid event='$event'"
        }

        this.pendingEventList = this.pendingEventList.let {
            val newList = it.toMutableList()
            newList.add(0, event)
            newList
        }
    }

    @AnyThread
    private fun pushBack(event: Event) = lock.withLock {
        require(validate(event)) {
            "Invalid event='$event'"
        }

        this.pendingEventList = this.pendingEventList.let {
            val newList = it.toMutableList()
            newList.add(event)
            newList
        }
    }

    @UiThread
    private fun broadcast() {
        assertUIThread()

        if (!isActive) {
            return
        }

        lock.withLock {
            val pending = pendingEventList.toMutableList()
            if (pending.isEmpty()) {
                return
            }
            val next = pending.removeAt(0)

            // save state.
            pendingEventList = pending

            // broadcast
            subject.onNext(next)
        }
        // re-run
        UIHandler.post {
            broadcast()
        }
    }

    /**
     * Pause this stream.
     */
    @UiThread
    fun pauseStream() {
        assertUIThread()
        this.mode = StreamMode.Pause
    }

    /**
     * Resume this stream.
     */
    @UiThread
    fun resumeStream() {
        assertUIThread()
        this.mode = StreamMode.Auto
        UIHandler.post {
            broadcast()
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
        assertUIThread()
        try {
            return subject.observeOn(AndroidSchedulers.mainThread())
                    .subscribe(onNext)
                    .with(owner)
        } finally {
            broadcast()
        }
    }

    /**
     * Make channel for Testing.
     */
    @UiThread
    fun testChannel(dispatcher: CoroutineDispatcher = Dispatchers.Main): Channel<Event> {
        try {
            return subject.observeOn(AndroidSchedulers.mainThread()).toChannel(dispatcher)
        } finally {
            UIHandler.postOrRun {
                broadcast()
            }
        }
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
