package com.swooby.alfred2017.sources

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.swooby.alfred2017.AlfredApp
import com.swooby.alfred2017.core.ingest.RawEvent
import com.swooby.alfred2017.data.EventEntity
import com.swooby.alfred2017.data.Sensitivity
import com.swooby.alfred2017.util.Ulids
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MediaSessionsSource(
    private val ctx: Context,
    private val app: AlfredApp
) {
    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listener = MediaSessionListener()
    private val controllerCallbacks = mutableMapOf<MediaController, MediaControllerCallback>()
    private val tracker = MediaSessionDurationTracker()

    fun start() {
        refreshSessions()
        msm.addOnActiveSessionsChangedListener(
            listener,
            ComponentName(ctx, com.swooby.alfred2017.sources.NotifSvc::class.java)
        )
    }

    fun stop() {
        msm.removeOnActiveSessionsChangedListener(listener)
        controllerCallbacks.keys.toList().forEach { detach(it) }
        controllerCallbacks.clear()
    }

    private fun refreshSessions() {
        val controllers = msm.getActiveSessions(
            ComponentName(ctx, com.swooby.alfred2017.sources.NotifSvc::class.java)
        ) ?: emptyList()
        controllers.forEach { if (!controllerCallbacks.containsKey(it)) attach(it) }
        controllerCallbacks.keys.toList().forEach { if (!controllers.contains(it)) detach(it) }
    }

    private fun attach(controller: MediaController) {
        val cb = MediaControllerCallback(controller)
        controllerCallbacks[controller] = cb
        controller.registerCallback(cb)
        controller.playbackState?.let { state ->
            if (state.state == PlaybackState.STATE_PLAYING) emitMediaStart(controller, controller.metadata, state)
        }
    }

    private fun detach(controller: MediaController) {
        controllerCallbacks.remove(controller)?.let { controller.unregisterCallback(it) }
    }

    private inner class MediaSessionListener : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            refreshSessions()
        }
    }

    private inner class MediaControllerCallback(
        private val c: MediaController
    ) : MediaController.Callback() {

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state ?: return
            val md = c.metadata
            when (state.state) {
                PlaybackState.STATE_PLAYING -> emitMediaStart(c, md, state)
                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_STOPPED,
                PlaybackState.STATE_NONE -> emitMediaStop(c, md, state)
                else -> {}
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val st = c.playbackState
            if (st?.state == PlaybackState.STATE_PLAYING) emitMediaStart(c, metadata, st)
        }
    }

    private fun emitMediaStart(c: MediaController, md: MediaMetadata?, st: PlaybackState) {
        val span = tracker.onStart(c, st)
        val pkg = c.packageName ?: "unknown"
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val durMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L
        val posMs = st.position.coerceAtLeast(0L)

        val ev = EventEntity(
            eventId = Ulids.newUlid(),
            schemaVer = 1,
            userId = "u_local",
            deviceId = "android:device",
            appPkg = pkg,
            component = "media_session",
            eventType = "media.start",
            eventCategory = "media",
            eventAction = "start",
            subjectEntity = "track",
            subjectEntityId = (title + "::" + artist + "::" + album).ifBlank { null },
            tsStart = span.startWall,
            api = "MediaSession",
            sensitivity = if (title.isNotBlank() || artist.isNotBlank()) Sensitivity.CONTENT else Sensitivity.METADATA,
            attributes = buildJsonObject {
                put("title", title)
                put("artist", artist)
                put("album", album)
                put("source_app", pkg)
                put("output_route", routeName(c))
            },
            metrics = buildJsonObject {
                put("position_ms", posMs.toInt())
                if (durMs > 0) put("track_duration_ms", durMs.toInt())
                put("volume_stream_music", currentMusicVolume(ctx))
            },
            tags = listOf("music","now_playing"),
            sessionId = span.sessionId
        )
        app.ingest.submit(RawEvent(ev, fingerprint = pkg + "|" + title + "|" + artist + "|" + album + "|" + span.sessionId + "|start", coalesceKey = "media:" + pkg + ":now_playing"))
    }

    private fun emitMediaStop(c: MediaController, md: MediaMetadata?, st: PlaybackState) {
        val q = tracker.onStop(c, st) ?: return
        val pkg = c.packageName ?: "unknown"
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""

        val ev = EventEntity(
            eventId = Ulids.newUlid(),
            schemaVer = 1,
            userId = "u_local",
            deviceId = "android:device",
            appPkg = pkg,
            component = "media_session",
            eventType = "media.stop",
            eventCategory = "media",
            eventAction = "stop",
            subjectEntity = "track",
            subjectEntityId = (title + "::" + artist + "::" + album).ifBlank { null },
            tsStart = q.tsStart,
            tsEnd = q.tsEnd,
            durationMs = q.durationMs,
            api = "MediaSession",
            sensitivity = if (title.isNotBlank() || artist.isNotBlank()) Sensitivity.CONTENT else Sensitivity.METADATA,
            attributes = buildJsonObject {
                put("title", title)
                put("artist", artist)
                put("album", album)
                put("source_app", pkg)
                put("output_route", routeName(c))
            },
            metrics = buildJsonObject {
                put("played_ms", q.playedMs.toInt())
                put("volume_stream_music", currentMusicVolume(ctx))
            },
            tags = listOf("music"),
            sessionId = q.sessionId
        )
        app.ingest.submit(RawEvent(ev, fingerprint = pkg + "|" + title + "|" + artist + "|" + album + "|" + q.sessionId + "|stop", coalesceKey = "media:" + pkg + ":now_playing"))
    }

    private fun routeName(c: MediaController): String {
        val info = c.playbackInfo ?: return "unknown"
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