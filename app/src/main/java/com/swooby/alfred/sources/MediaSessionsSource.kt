package com.swooby.alfred.sources

import android.content.ComponentName
import android.content.Context
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.BuildConfig
import com.swooby.alfred.core.ingest.RawEvent
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.data.Sensitivity
import com.swooby.alfred.util.FooLog
import com.swooby.alfred.util.FooString
import com.swooby.alfred.util.Ulids
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


/**
 * TODO: Add android.media.session.MediaController.TransportControls
 * TODO: Investigate <a href="{@docRoot}jetpack/androidx.html">AndroidX</a><a href="{@docRoot}media/media3/session/control-playback">Media3 session Library</a>
 */
class MediaSessionsSource(
    private val ctx: Context,
    private val app: AlfredApp
) {
    companion object {
        private val TAG = FooLog.TAG(MediaSessionsSource::class.java)
        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        private val LOG_CONTROLLERS = false && BuildConfig.DEBUG
        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        private val LOG_MEDIA_SESSION_CHANGED = true && BuildConfig.DEBUG
        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        private val LOG_MEDIA_CONTROLLER = true && BuildConfig.DEBUG
    }
    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listener = MediaSessionListener()
    private val controllerCallbacks = mutableMapOf<MediaController, MediaControllerCallback>()
    private val tracker = MediaSessionDurationTracker()

    fun start(caller: String) {
        try {
            FooLog.v(TAG, "start(caller=${FooString.quote(caller)})")
            refreshSessions("$caller->start")
            msm.addOnActiveSessionsChangedListener(
                listener,
                ComponentName(ctx, NotificationsSource::class.java)
            )
        } catch (se: SecurityException) {
            // No access yet; caller should have gated this, but be safe.
            FooLog.w(TAG, "refreshSessions: SecurityException", se)
        }
    }

    fun stop(caller: String) {
        FooLog.v(TAG, "stop(caller=${FooString.quote(caller)})")
        msm.removeOnActiveSessionsChangedListener(listener)
        controllerCallbacks.keys.toList().forEach { detach(it) }
        controllerCallbacks.clear()
    }

    /**
     * Copy of simple [android.media.session.MediaController]`.controlsSameSession` to unhide it.
     *
     * `controlsSameSession` has an annotation that says:
     * ```java
     * @UnsupportedAppUsage(publicAlternatives = "Check equality of {@link #getSessionToken() tokens} "
     *             + "instead.", maxTargetSdk = Build.VERSION_CODES.R)
     * ```
     */
    private fun controlsSameSession(c1: MediaController, c2: MediaController): Boolean {
        return c1.sessionToken == c2.sessionToken
    }

    /**
     * Needed because [Map.containsKey] does not work for a [android.media.session.MediaController]
     * key because [android.media.session.MediaController] does not have a [Any.equals] or
     * [Any.hashCode] that compares [android.media.session.MediaController.getSessionToken].
     */
    private fun contains(c: Collection<MediaController>, key: MediaController): Boolean {
        c.forEach { controller ->
            if (controlsSameSession(controller, key)) return true
        }
        return false
    }

    private fun refreshSessions(caller: String, controllers: MutableList<MediaController>? = null) {
        FooLog.v(TAG, "refreshSessions(caller=${FooString.quote(caller)}, controllers=...)")
        try {
            val controllers = controllers ?: msm.getActiveSessions(ComponentName(ctx, NotificationsSource::class.java))
            if (LOG_CONTROLLERS) {
                FooLog.v(TAG, "refreshSessions: controllers(${controllers.size})=${controllers.map { toString(it) }}")
                FooLog.e(TAG, "refreshSessions: BEFORE controllerCallback.keys(${controllerCallbacks.keys.size})=${controllerCallbacks.keys.map { toString(it) }}")
            }
            controllers.forEach {
                if (LOG_CONTROLLERS) {
                    FooLog.v(TAG, "refreshSessions: controller=${toString(it)}")
                }
                if (!contains(controllerCallbacks.keys, it)) {
                    FooLog.v(TAG, "refreshSessions: controller not attached; attaching...")
                    attach(it)
                } else {
                    FooLog.v(TAG, "refreshSessions: controller already attached; ignoring")
                }
            }
            // Remove callbacks for any controllers that are no longer active...
            controllerCallbacks.keys.toList().forEach { if (!contains(controllers, it)) detach(it) }
            if (LOG_CONTROLLERS) {
                FooLog.e(TAG, "refreshSessions:  AFTER controllerCallback.keys(${controllerCallbacks.keys.size})=${controllerCallbacks.keys.map { toString(it) }}")
            }
        } catch (se: SecurityException) {
            FooLog.w(TAG, "refreshSessions: SecurityException", se)
        }
    }

    private fun toString(sessionToken: MediaSession.Token): String {
        return "{ hashCode()=${sessionToken.hashCode()} }"
    }

    private fun toString(mediaController: MediaController): String {
        return "{ token=${toString(mediaController.sessionToken)}" +
                ", sessionInfo=${mediaController.sessionInfo}" +
                ", tag=${FooString.quote(mediaController.tag)}" +
                ", packageName=${FooString.quote(mediaController.packageName)}" +
                ", hashCode()=${mediaController.hashCode()} }"
    }

    private fun toString(state: PlaybackState?): String {
        return if (state == null) "null" else mediaSessionPlaybackStateToString(state.state)
    }

    private fun mediaSessionPlaybackStateToString(state: Int): String {
        return when (state) {
            PlaybackState.STATE_NONE -> "STATE_NONE"
            PlaybackState.STATE_STOPPED -> "STATE_STOPPED"
            PlaybackState.STATE_PAUSED -> "STATE_PAUSED"
            PlaybackState.STATE_PLAYING -> "STATE_PLAYING"
            PlaybackState.STATE_FAST_FORWARDING -> "STATE_FAST_FORWARDING"
            PlaybackState.STATE_REWINDING -> "STATE_REWINDING"
            PlaybackState.STATE_BUFFERING -> "STATE_BUFFERING"
            PlaybackState.STATE_ERROR -> "STATE_ERROR"
            PlaybackState.STATE_CONNECTING -> "STATE_CONNECTING"
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "STATE_SKIPPING_TO_PREVIOUS"
            PlaybackState.STATE_SKIPPING_TO_NEXT -> "STATE_SKIPPING_TO_NEXT"
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "STATE_SKIPPING_TO_QUEUE_ITEM"
            else -> "UNKNOWN"
        }.let { "$it($state)" }
    }

    private fun attach(controller: MediaController) {
        FooLog.v(TAG, "+attach(${toString(controller)}")
        val cb = MediaControllerCallback(controller)
        controllerCallbacks[controller] = cb
        controller.registerCallback(cb)
        controller.playbackState?.let { state ->
            //FooLog.i(TAG, "attach: state=${mediaSessionPlaybackStateToString(state.state)}")
            when (state.state) {
                PlaybackState.STATE_PLAYING -> {
                    FooLog.v(TAG, "attach: STATE_PLAYING; emitMediaStart(...)")
                    emitMediaStart(controller, controller.metadata, state)
                }
                else -> {
                    FooLog.v(TAG, "attach: not STATE_PLAYING; ignore")
                }
            }
        }
        FooLog.v(TAG, "-attach(...)")
    }

    private fun detach(controller: MediaController) {
        FooLog.d(TAG, "+detach(${toString(controller)}")
        controllerCallbacks.remove(controller)?.let { controller.unregisterCallback(it) }
        FooLog.d(TAG, "-detach(...)")
    }

    private inner class MediaSessionListener : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            if (LOG_MEDIA_SESSION_CHANGED) {
                FooLog.d(TAG, "#MEDIA onActiveSessionsChanged: controllers.size=${controllers?.size}")
            }
            refreshSessions("onActiveSessionsChanged", controllers)
        }
    }

    private inner class MediaControllerCallback(private val c: MediaController) : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (LOG_MEDIA_CONTROLLER) {
                FooLog.d(TAG, "#MEDIA onPlaybackStateChanged(controller=${toString(c)}, state=${toString(state)})")
            }
            if (state == null) return
            val md = c.metadata
            when (state.state) {
                PlaybackState.STATE_PLAYING -> emitMediaStart(c, md, state)
                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_STOPPED,
                PlaybackState.STATE_NONE -> emitMediaStop(c, md, state)
                else -> {}
            }
        }

        /**
         * More expanded version of [android.media.MediaDescription.toString]
         */
        private fun toString(md: MediaDescription?): String {
            if (md == null) return "null"
            val sb = StringBuilder("{")
            md.mediaId?.let {
                sb.append(" mediaId=${FooString.quote(it)},")
            }
            md.mediaUri?.let {
                sb.append(" mediaUri=${FooString.quote(it)},")
            }
            sb.append(" title=${FooString.quote(md.title)},")
            md.subtitle?.let {
                sb.append(" subtitle=${FooString.quote(md.subtitle)},")
            }
            md.description?.let {
                sb.append(" description=${FooString.quote(md.description)},")
            }
            md.extras?.let {
                sb.append(" extras=${FooString.quote(md.extras)},")
            }
            return "${sb.toString().trimEnd { it == ',' }} }"
        }

        private fun toString(md: MediaMetadata?): String {
            if (md == null) return "null"
            val sb = StringBuilder("{")
            // TODO: Expand this to show more than just [android.media.MediaMetadata.getDescription]
            sb.append("..., ")
            sb.append(" description=${toString(md.description)},")
            sb.append("...")
            return "${sb.toString().trimEnd { it == ',' }} }"
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (LOG_MEDIA_CONTROLLER) {
                FooLog.d(TAG, "#MEDIA onMetadataChanged(controller=${toString(c)}, metadata=${toString(metadata)})")
            }
            val state = c.playbackState
            if (state == null) return
            when (state.state) {
                PlaybackState.STATE_PLAYING -> emitMediaStart(c, metadata, state)
                else -> {}
            }
        }
    }

    private fun emitMediaStart(c: MediaController, md: MediaMetadata?, state: PlaybackState) {
        val span = tracker.onStart(c, state)
        val sessionId = span.sessionId
        val eventId = Ulids.newUlid()
        val pkg = c.packageName ?: "unknown"
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val eventCategory = "media"
        val eventType = SourceEventTypes.MEDIA_START
        val subjectEntityId = subjectEntityId(title, artist, album)
        val api = "MediaSession"
        val sensitivity = if (title.isNotBlank() || artist.isNotBlank()) Sensitivity.CONTENT else Sensitivity.METADATA
        val attributes = buildJsonObject {
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("source_app", pkg)
            put("output_route", routeName(c))
        }
        val musicVolume = currentMusicVolume(ctx)

        val action = "start"
        val durMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L
        val posMs = state.position.coerceAtLeast(0L)
        val metrics = buildJsonObject {
            put("position_ms", posMs.toInt())
            if (durMs > 0) put("track_duration_ms", durMs.toInt())
            put("volume_stream_music", musicVolume)
        }

        val ev = EventEntity(
            eventId = eventId,
            schemaVer = 1,
            userId = "u_local",
            deviceId = "android:device",
            appPkg = pkg,
            component = SourceComponentIds.MEDIA_SOURCE,
            eventType = eventType,
            eventCategory = eventCategory,
            eventAction = action,
            subjectEntity = "track",
            subjectEntityId = subjectEntityId,
            tsStart = span.startWall,
            api = api,
            sensitivity = sensitivity,
            attributes = attributes,
            metrics = metrics,
            tags = listOf("music", "now_playing"),
            sessionId = sessionId
        )
        app.ingest.submit(
            RawEvent(
                ev,
                fingerprint =  fingerprint(pkg, title, artist, album, sessionId, action),
                coalesceKey = coalesceKey(pkg)
            )
        )
    }

    private fun emitMediaStop(c: MediaController, md: MediaMetadata?, st: PlaybackState) {
        val quad = tracker.onStop(c, st) ?: return
        val eventId = Ulids.newUlid()
        val sessionId = quad.sessionId
        val pkg = c.packageName ?: "unknown"
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val eventCategory = "media"
        val eventType = SourceEventTypes.MEDIA_STOP
        val subjectEntityId = subjectEntityId(title, artist, album)
        val api = "MediaSession"
        val sensitivity = if (title.isNotBlank() || artist.isNotBlank()) Sensitivity.CONTENT else Sensitivity.METADATA
        val attributes = buildJsonObject {
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("source_app", pkg)
            put("output_route", routeName(c))
        }
        val musicVolume = currentMusicVolume(ctx)

        val action = "stop"
        val metrics = buildJsonObject {
            put("played_ms", quad.playedMs.toInt())
            put("volume_stream_music", musicVolume)
        }

        val ev = EventEntity(
            eventId = eventId,
            schemaVer = 1,
            userId = "u_local",
            deviceId = "android:device",
            appPkg = pkg,
            component = SourceComponentIds.MEDIA_SOURCE,
            eventType = eventType,
            eventCategory = eventCategory,
            eventAction = action,
            subjectEntity = "track",
            subjectEntityId = subjectEntityId,
            tsStart = quad.tsStart,
            tsEnd = quad.tsEnd,
            durationMs = quad.durationMs,
            api = api,
            sensitivity = sensitivity,
            attributes = attributes,
            metrics = metrics,
            tags = listOf("music"),
            sessionId = sessionId
        )
        app.ingest.submit(
            RawEvent(
                ev,
                fingerprint =  fingerprint(pkg, title, artist, album, sessionId, action),
                coalesceKey = coalesceKey(pkg)
            )
        )
    }

    private fun fingerprint(pkg: String, title: String, artist: String, album: String, sessionId: String, action: String): String {
        return "$pkg|$title|$artist|$album|$sessionId|$action"
    }

    private fun coalesceKey(pkg: String): String {
        return "media:$pkg:now_playing"
    }

    private fun subjectEntityId(title: String, artist: String, album: String): String? {
        return "$title::$artist::$album".ifBlank { null }
    }

    private fun routeName(c: MediaController): String {
        val info = c.playbackInfo
        return when (info.playbackType) {
            MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL -> "device"
            MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE -> "cast"
            else -> "unknown"
        }
    }

    private fun currentMusicVolume(ctx: Context): Int {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        return am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    }
}
