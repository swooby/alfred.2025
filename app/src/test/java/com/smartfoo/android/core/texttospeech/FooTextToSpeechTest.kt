package com.smartfoo.android.core.texttospeech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import com.smartfoo.android.core.media.FooAudioFocusController
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FooTextToSpeechTest {
    private lateinit var subject: FooTextToSpeech
    private lateinit var audioAttributesStub: AudioAttributes
    private lateinit var bundleStub: Bundle
    private lateinit var contextStub: Context
    private lateinit var audioManagerStub: AudioManager
    private lateinit var audioFocusControllerStub: FooAudioFocusController
    private var originalAudioFocusController: FooAudioFocusController? = null
    private var originalAudioFocusCallbacks: FooAudioFocusController.Callbacks? = null

    @BeforeTest
    fun setUp() {
        audioAttributesStub = mockk(relaxed = true)
        bundleStub = mockk(relaxed = true)
        contextStub = mockk(relaxed = true)
        audioManagerStub = mockk(relaxed = true)
        audioFocusControllerStub = mockk(relaxed = true)
        FooTextToSpeech.audioAttributesFactory = { audioAttributesStub }
        FooTextToSpeech.bundleFactory = { bundleStub }
        mockkStatic(Log::class)
        mockkStatic(TextToSpeech::class)
        every { Log.v(any(), any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        every { Log.i(any(), any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { TextToSpeech.getMaxSpeechInputLength() } returns 4_000

        every { audioManagerStub.requestAudioFocus(any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { audioManagerStub.abandonAudioFocusRequest(any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { contextStub.getSystemService(Context.AUDIO_SERVICE) } returns audioManagerStub
        every {
            audioFocusControllerStub.acquire(
                context = any(),
                audioAttributes = any(),
                focusGainType = any(),
                callbacks = any(),
                tag = any(),
            )
        } returns null

        subject = FooTextToSpeech.instance
        originalAudioFocusController = getPrivateField("audioFocusController")
        originalAudioFocusCallbacks = getPrivateField("audioFocusControllerCallbacks")
        setPrivateField("applicationContext", contextStub)
        setPrivateField("tts", mockk<TextToSpeech>(relaxed = true))
        setPrivateField("isStarted", true)
        setPrivateField("isInitialized", false)
        setPrivateField("currentUtterance", null)
        setPrivateField("audioFocusControllerHandle", null)
        setPrivateField("audioFocusController", audioFocusControllerStub)
        setPrivateField(
            "audioFocusControllerCallbacks",
            object : FooAudioFocusController.Callbacks() {},
        )
        setPrivateField("nextSequenceId", 0L)
        utteranceQueue().clear()
        sequenceStates().clear()
    }

    @AfterTest
    fun tearDown() {
        if (this::subject.isInitialized) {
            utteranceQueue().clear()
            sequenceStates().clear()
            setPrivateField("isStarted", false)
            setPrivateField("isInitialized", false)
            setPrivateField("tts", null)
            setPrivateField("applicationContext", null)
            setPrivateField("currentUtterance", null)
            setPrivateField("audioFocusControllerHandle", null)
            originalAudioFocusController?.let { setPrivateField("audioFocusController", it) }
            originalAudioFocusCallbacks?.let { setPrivateField("audioFocusControllerCallbacks", it) }
        }
        FooTextToSpeech.resetAudioAttributesFactory()
        FooTextToSpeech.resetBundleFactory()
        unmockkStatic(TextToSpeech::class)
        unmockkStatic(Log::class)
    }

    @Test
    fun sequenceEnqueue_emptyBuilder_returnsNullAndDoesNotEnqueue() {
        val builder = FooTextToSpeechBuilder()
        assertTrue(builder.isEmpty, "Builder should start empty for this scenario")
        val sequenceId = subject.sequenceEnqueue(builder = builder)
        assertNull(sequenceId, "Empty builders should not produce a sequence id")
        assertTrue(builder.isEmpty, "Builder must remain empty after invocation")
        assertTrue(utteranceQueue().isEmpty(), "Queue should remain untouched when nothing is enqueued")
    }

    @Test
    fun speakBuilder_enqueuesTextAndTrailingSilenceWithSharedSequenceId() {
        val sequenceId = subject.speak("Hello world")
        assertNotNull(sequenceId)
        val enqueued = utteranceQueue().toList()
        assertEquals(2, enqueued.size, "Expected speech followed by trailing silence")
        enqueued.forEach { utterance ->
            assertEquals(sequenceId, utterance.sequenceId(), "Sequence id should remain consistent across parts")
        }
        assertEquals("Text", enqueued.first().javaClass.simpleName)
        assertEquals("Silence", enqueued.last().javaClass.simpleName)
        assertTrue(sequenceStates().containsKey(sequenceId), "Sequence state should be tracked for enqueued items")
    }

    @Test
    fun stopSequence_clearsQueuedItemsForSequence() {
        val sequenceId = subject.speak("Stop me")
        val canceled = subject.sequenceStop(requireNotNull(sequenceId))
        assertTrue(canceled)
        assertTrue(utteranceQueue().isEmpty(), "Queue should be empty after stopping the sequence")
        assertTrue(sequenceStates().isEmpty(), "Sequence state should be cleared once the sequence stops")
    }

    @Test
    fun speakWithClearPlacement_replacesExistingQueue() {
        val firstId = subject.speak("First")
        assertNotNull(firstId)
        assertEquals(2, utteranceQueue().size)
        val secondId = subject.speak("Second", placement = FooTextToSpeech.QueuePlacement.CLEAR)
        assertNotNull(secondId)
        assertNotEquals(firstId, secondId, "Clearing should yield a new sequence id")
        val enqueued = utteranceQueue().toList()
        assertEquals(2, enqueued.size, "New sequence should replace previous items")
        enqueued.forEach { utterance ->
            assertEquals(secondId, utterance.sequenceId(), "All queued parts must target the replacement sequence")
        }
    }

    @Test
    fun speakNext_insertsAheadOfExistingQueue() {
        val firstId = subject.speak("First")
        assertNotNull(firstId)
        assertEquals(2, utteranceQueue().size)
        val nextId = subject.speak("Second", placement = FooTextToSpeech.QueuePlacement.NEXT)
        assertNotNull(nextId)
        val enqueued = utteranceQueue().toList()
        assertEquals(4, enqueued.size)
        enqueued.subList(0, 2).forEach { utterance ->
            assertEquals(nextId, utterance.sequenceId(), "NEXT placement should add items to the front of the queue")
        }
        enqueued.subList(2, 4).forEach { utterance ->
            assertEquals(firstId, utterance.sequenceId(), "Existing queue items should remain after NEXT placement")
        }
    }

    @Test
    fun speakImmediate_interruptsCurrentSequence() {
        setPrivateField("isInitialized", true)
        val originalId = subject.speak("Original")
        assertNotNull(originalId)
        val queue = utteranceQueue()
        val nowPlaying = queue.removeFirst()
        setPrivateField("currentUtterance", nowPlaying)
        val immediateId = subject.speak("Immediate", placement = FooTextToSpeech.QueuePlacement.IMMEDIATE)
        assertNotNull(immediateId)
        val current = getCurrentUtterance()
        assertNotNull(current, "Immediate placement should start the new sequence right away")
        assertEquals(immediateId, current.sequenceId(), "Current utterance must belong to the immediate sequence")
        assertTrue(utteranceQueue().all { it.sequenceId() == immediateId }, "Interrupted sequence should be purged from the queue")
    }

    @Test
    fun speak_notifiesSequenceCallbacksOnLifecycle() {
        setPrivateField("isInitialized", true)
        val starts = mutableListOf<String>()
        val completes = mutableListOf<Triple<String, Boolean, Int>>()
        val startLatch = CountDownLatch(1)
        val completeLatch = CountDownLatch(1)
        val callbacks =
            object : FooTextToSpeech.SequenceCallbacks {
                override fun onSequenceStart(sequenceId: String) {
                    starts += sequenceId
                    startLatch.countDown()
                }

                override fun onSequenceComplete(
                    sequenceId: String,
                    neverStarted: Boolean,
                    errorCode: Int,
                ) {
                    completes += Triple(sequenceId, neverStarted, errorCode)
                    completeLatch.countDown()
                }
            }
        val sequenceId = subject.speak("Notify me", callbacks = callbacks)
        assertNotNull(sequenceId)
        assertTrue(startLatch.await(1, TimeUnit.SECONDS), "Sequence start callback should fire within timeout")
        assertEquals(listOf(sequenceId), starts, "Sequence start should be reported exactly once")
        val canceled = subject.sequenceStop(sequenceId)
        assertTrue(canceled, "Stopping the sequence should succeed")
        assertTrue(completeLatch.await(1, TimeUnit.SECONDS), "Sequence completion callback should fire within timeout")
        assertEquals(
            listOf(Triple(sequenceId, false, TextToSpeech.STOPPED)),
            completes,
            "Sequence completion should be reported exactly once with expected state",
        )
    }

    private fun setPrivateField(
        name: String,
        value: Any?,
    ) {
        val field = FooTextToSpeech::class.java.getDeclaredField(name)
        field.isAccessible = true
        try {
            field.set(subject, value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Failed setting field $name with value=$value (${value?.javaClass})", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(name: String): T? {
        val field = FooTextToSpeech::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(subject) as T?
    }

    @Suppress("UNCHECKED_CAST")
    private fun utteranceQueue(): ArrayDeque<Any> {
        val field = FooTextToSpeech::class.java.getDeclaredField("utteranceQueue")
        field.isAccessible = true
        return field.get(subject) as ArrayDeque<Any>
    }

    private fun getCurrentUtterance(): Any? {
        val field = FooTextToSpeech::class.java.getDeclaredField("currentUtterance")
        field.isAccessible = true
        return field.get(subject)
    }

    @Suppress("UNCHECKED_CAST")
    private fun sequenceStates(): MutableMap<String, Any> {
        val field = FooTextToSpeech::class.java.getDeclaredField("sequenceStates")
        field.isAccessible = true
        return field.get(subject) as MutableMap<String, Any>
    }

    private fun Any.sequenceId(): String = readUtteranceProperty("sequenceId") as String

    private fun Any.readUtteranceProperty(name: String): Any? {
        var clazz: Class<*>? = this.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(name)
                field.isAccessible = true
                return field.get(this)
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        error("Field $name not found on $this")
    }
}
