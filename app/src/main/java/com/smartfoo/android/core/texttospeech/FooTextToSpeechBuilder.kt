package com.smartfoo.android.core.texttospeech

import android.content.Context
import android.speech.tts.TextToSpeech
import com.smartfoo.android.core.collections.FooCollections
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import java.util.LinkedList

class FooTextToSpeechBuilder {
    companion object {
        private val TAG = FooLog.TAG(FooTextToSpeechBuilder::class.java)

        const val SILENCE_WORD_BREAK_MILLIS = 300
        const val SILENCE_SENTENCE_BREAK_MILLIS = 500
        const val SILENCE_PARAGRAPH_BREAK_MILLIS = 750

        /**
         * 4000 in API36
         *
         * @see [TextToSpeech.getMaxSpeechInputLength]
         */
        val MAX_SPEECH_INPUT_LENGTH = TextToSpeech.getMaxSpeechInputLength()
    }

    abstract class FooTextToSpeechPart

    class FooTextToSpeechPartSpeech(text: String) : FooTextToSpeechPart() {
        companion object {
            private val TAG = FooTextToSpeechPartSpeech::class.java.simpleName
        }

        val text = if (text.length > MAX_SPEECH_INPUT_LENGTH) {
            FooLog.w(TAG, "FooTextToSpeechPartSpeech: text > $MAX_SPEECH_INPUT_LENGTH; trimming text to $MAX_SPEECH_INPUT_LENGTH characters")
            text.substring(0, MAX_SPEECH_INPUT_LENGTH)
        } else {
            text
        }
            // Remove any unspeakable/unprintable characters
            //noinspection TrimLambda
            .trim { it <= ' ' }

        override fun toString() = "text=${FooString.quote(text)}"

        override fun equals(other: Any?) =
            other is FooTextToSpeechPartSpeech && FooString.equals(text, other.text)

        override fun hashCode() = text.hashCode()
    }

    class FooTextToSpeechPartSilence(val durationMillis: Int) : FooTextToSpeechPart() {
        override fun toString() = "durationMillis=$durationMillis"

        override fun equals(other: Any?) =
            other is FooTextToSpeechPartSilence && durationMillis == other.durationMillis

        override fun hashCode() = durationMillis.hashCode()
    }

    class FooTextToSpeechPartEarcon(val earcon: String) : FooTextToSpeechPart() {
        override fun toString() = "earcon=$earcon"

        override fun equals(other: Any?) =
            other is FooTextToSpeechPartEarcon && FooString.equals(earcon, other.earcon)

        override fun hashCode() = earcon.hashCode()
    }

    private var context: Context? = null
    private val parts = mutableListOf<FooTextToSpeechPart>()

    @Suppress("unused")
    constructor()

    @Suppress("unused")
    constructor(context: Context) {
        this.context = context
    }

    constructor(text: String) {
        appendSpeech(text)
    }

    @Suppress("unused")
    constructor(context: Context, text: String) {
        this.context = context
        appendSpeech(text)
    }

    @Suppress("unused")
    constructor(context: Context, textResId: Int, vararg formatArgs: Any?) {
        this.context = context
        appendSpeech(context, textResId, *formatArgs)
    }

    @Suppress("unused")
    constructor(builder: FooTextToSpeechBuilder) {
        append(builder)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append('[')
        val iterator: Iterator<FooTextToSpeechPart> = parts.iterator()
        while (iterator.hasNext()) {
            val part = iterator.next()
            sb.append(part)
            if (iterator.hasNext()) {
                sb.append(", ")
            }
        }
        sb.append(']')
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        return other is FooTextToSpeechBuilder && FooCollections.identical(parts, other.parts)
    }

    override fun hashCode(): Int {
        return FooCollections.hashCode(parts)
    }

    @Suppress("unused")
    val isEmpty: Boolean
        get() = parts.isEmpty()

    @Suppress("unused")
    val numberOfParts: Int
        get() = parts.size

    @Suppress("unused")
    fun appendSpeech(context: Context, textResId: Int, vararg formatArgs: Any?): FooTextToSpeechBuilder {
        this.context = context
        return appendSpeech(textResId, formatArgs)
    }

    fun appendSpeech(textResId: Int, vararg formatArgs: Any?): FooTextToSpeechBuilder {
        val context = this.context ?: throw IllegalStateException("Must first call FooTextToSpeechBuilder(context, ...) or appendSpeech(context, ...)")
        return appendSpeech(context.getString(textResId, *formatArgs))
    }

    @Suppress("unused")
    fun appendSpeech(text: CharSequence): FooTextToSpeechBuilder {
        return appendSpeech(text.toString())
    }

    fun appendSpeech(text: String): FooTextToSpeechBuilder {
        return append(FooTextToSpeechPartSpeech(text))
    }

    @Suppress("unused")
    fun appendSilenceWordBreak(): FooTextToSpeechBuilder {
        return appendSilence(SILENCE_WORD_BREAK_MILLIS)
    }

    fun appendSilenceSentenceBreak(): FooTextToSpeechBuilder {
        return appendSilence(SILENCE_SENTENCE_BREAK_MILLIS)
    }

    @Suppress("unused")
    fun appendSilenceParagraphBreak(): FooTextToSpeechBuilder {
        return appendSilence(SILENCE_PARAGRAPH_BREAK_MILLIS)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun appendSilence(durationInMs: Int): FooTextToSpeechBuilder {
        return append(FooTextToSpeechPartSilence(durationInMs))
    }

    fun append(part: FooTextToSpeechPart): FooTextToSpeechBuilder {
        when (part) {
            is FooTextToSpeechPartSpeech -> if (part.text.isNotBlank()) {
                parts.add(part)
            } else {
                FooLog.w(TAG, "append: part.text.isBlank(); ignoring")
            }
            is FooTextToSpeechPartSilence -> if (part.durationMillis > 0) {
                parts.add(part)
            } else {
                FooLog.w(TAG, "append: part.durationMillis <= 0; ignoring")
            }
            is FooTextToSpeechPartEarcon -> if (part.earcon.isNotBlank()) {
                parts.add(part)
            } else {
                FooLog.w(TAG, "append: part.earcon.isBlank(); ignoring")
            }
        }
        return this
    }

    fun append(builder: FooTextToSpeechBuilder): FooTextToSpeechBuilder {
        context = builder.context
        for (part in builder.parts) {
            append(part)
        }
        return this
    }

    /**
     * @param ensureEndsWithSilence if true and the last part is NOT a [FooTextToSpeechPartSilence] then call [appendSilenceSentenceBreak]
     * @return a copy of the parts
     */
    fun build(ensureEndsWithSilence: Boolean = false): List<FooTextToSpeechPart> {
        if (ensureEndsWithSilence && parts.last() !is FooTextToSpeechPartSilence) {
            appendSilenceSentenceBreak()
        }
        val parts = LinkedList(parts)
        this.parts.clear()
        return parts
    }
}
