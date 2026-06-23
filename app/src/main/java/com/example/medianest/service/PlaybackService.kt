package com.example.medianest.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.medianest.MainActivity

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource

import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

class PlaybackService : MediaSessionService() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        val defaultMediaSourceFactory = DefaultMediaSourceFactory(this)
        val customMediaSourceFactory = object : androidx.media3.exoplayer.source.MediaSource.Factory {
            override fun createMediaSource(mediaItem: MediaItem): androidx.media3.exoplayer.source.MediaSource {
                val extras = mediaItem.requestMetadata.extras
                val audioUrl = extras?.getString("audio_url")
                val videoSource = defaultMediaSourceFactory.createMediaSource(mediaItem)
                if (!audioUrl.isNullOrEmpty()) {
                    val audioMediaItem = MediaItem.fromUri(audioUrl)
                    val audioSource = defaultMediaSourceFactory.createMediaSource(audioMediaItem)
                    return MergingMediaSource(videoSource, audioSource)
                }
                return videoSource
            }

            @Deprecated("Deprecated in Java", ReplaceWith("defaultMediaSourceFactory.supportedTypes"))
            override fun getSupportedTypes(): IntArray {
                return defaultMediaSourceFactory.supportedTypes
            }

            override fun setDrmSessionManagerProvider(
                drmSessionManagerProvider: DrmSessionManagerProvider
            ): androidx.media3.exoplayer.source.MediaSource.Factory {
                defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                return this
            }

            override fun setLoadErrorHandlingPolicy(
                loadErrorHandlingPolicy: LoadErrorHandlingPolicy
            ): androidx.media3.exoplayer.source.MediaSource.Factory {
                defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                return this
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(customMediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()
        player.setAudioAttributes(audioAttributes, true)
        exoPlayer = player

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }
}
