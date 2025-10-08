package com.smartfoo.android.core.texttospeech

import android.content.Context
import android.speech.tts.TextToSpeech
import com.smartfoo.android.core.collections.FooCollections
import com.smartfoo.android.core.FooString
import java.util.LinkedList

class FooTextToSpeechBuilder {
    companion object {
        const val SILENCE_WORD_BREAK_MILLIS = 300
        const val SILENCE_SENTENCE_BREAK_MILLIS = 500
        const val SILENCE_PARAGRAPH_BREAK_MILLIS = 750
        val MAX_SPEECH_INPUT_LENGTH = TextToSpeech.getMaxSpeechInputLength()
    }

    // package
    abstract class FooTextToSpeechPart

    // package
    class FooTextToSpeechPartSpeech(text: String?) : FooTextToSpeechPart() {
        val mText: String?
        init {
            @Suppress("NAME_SHADOWING")
            var text = text
            if (text != null) {
                require(text.length <= MAX_SPEECH_INPUT_LENGTH) {
                    "text.length must be <= FooTextToSpeechBuilder.MAX_SPEECH_INPUT_LENGTH($MAX_SPEECH_INPUT_LENGTH)"
                }
                text = text.trim { it <= ' ' }
                if ("" == text) {
                    text = null
                }
            }
            mText = text
        }

        override fun toString() = "mText=${FooString.quote(mText)}"

        override fun equals(other: Any?) =
            other is FooTextToSpeechPartSpeech && FooString.equals(mText, other.mText)

        override fun hashCode() = mText?.hashCode() ?: 0
    }

    // package
    class FooTextToSpeechPartSilence(val mSilenceDurationMillis: Int) : FooTextToSpeechPart() {
        override fun toString() = "mSilenceDurationMillis=$mSilenceDurationMillis"

        override fun equals(other: Any?) =
            other is FooTextToSpeechPartSilence && mSilenceDurationMillis == other.mSilenceDurationMillis

        override fun hashCode() = mSilenceDurationMillis
    }

    private val mParts: LinkedList<FooTextToSpeechPart> = LinkedList()

    private var mContext: Context? = null

    constructor()

    @JvmOverloads
    constructor(
        context: Context,
        text: String? = null) {
        mContext = context
        appendSpeech(text)
    }

    constructor(
        text: String?) {
        appendSpeech(text)
    }

    constructor(
        context: Context,
        textResId: Int,
        vararg formatArgs: Any?) {
        mContext = context
        appendSpeech(textResId, *formatArgs)
    }

    constructor(
        builder: FooTextToSpeechBuilder
    ) {
        append(builder)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append('[')
        val iterator: Iterator<FooTextToSpeechPart> = mParts.iterator()
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
        return other is FooTextToSpeechBuilder && FooCollections.identical(mParts, other.mParts)
    }

    override fun hashCode(): Int {
        return FooCollections.hashCode(mParts)
    }

    @Suppress("unused")
    val isEmpty: Boolean
        get() = mParts.isEmpty()

    val numberOfParts: Int
        get() = mParts.size

    @Suppress("unused")
    fun appendSpeech(context: Context, textResId: Int, vararg formatArgs: Any?): FooTextToSpeechBuilder {
        mContext = context
        return appendSpeech(textResId, formatArgs)
    }

    fun appendSpeech(textResId: Int, vararg formatArgs: Any?): FooTextToSpeechBuilder {
        val context = mContext ?: throw IllegalStateException("Must first call FooTextToSpeechBuilder(context, ...) or appendSpeech(context, ...)")
        return appendSpeech(context.getString(textResId, *formatArgs) as String?)
    }

    fun appendSpeech(text: CharSequence?): FooTextToSpeechBuilder {
        return appendSpeech(text.toString())
    }

    fun appendSpeech(text: String?): FooTextToSpeechBuilder {
        return append(FooTextToSpeechPartSpeech(text))
    }

    fun appendSilenceWordBreak(): FooTextToSpeechBuilder {
        return appendSilence(SILENCE_WORD_BREAK_MILLIS)
    }

    fun appendSilenceSentenceBreak(): FooTextToSpeechBuilder {
        return appendSilence(SILENCE_SENTENCE_BREAK_MILLIS)
    }

    fun appendSilenceParagraphBreak(): FooTextToSpeechBuilder {
        return appendSilence(SILENCE_PARAGRAPH_BREAK_MILLIS)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun appendSilence(durationInMs: Int): FooTextToSpeechBuilder {
        return append(FooTextToSpeechPartSilence(durationInMs))
    }

    fun append(part: FooTextToSpeechPart): FooTextToSpeechBuilder {
        if (part !is FooTextToSpeechPartSpeech || part.mText != null) {
            mParts.add(part)
        }
        return this
    }

    fun append(builder: FooTextToSpeechBuilder): FooTextToSpeechBuilder {
        mContext = builder.mContext
        for (mPart in builder.mParts) {
            append(mPart)
        }
        return this
    }

    fun build(): List<FooTextToSpeechPart> {
        val parts: List<FooTextToSpeechPart> = LinkedList(mParts)
        mParts.clear()
        return parts
    }
}
