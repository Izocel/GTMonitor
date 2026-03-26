package com.example.gtmonitor

import android.content.Context
import android.media.RingtoneManager
import android.media.Ringtone
import android.net.Uri

object NotificationSoundPlayer {
    private var muted = false
    private var ringtone: Ringtone? = null

    fun init(context: Context) {
        if (ringtone != null) return
        val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(context.applicationContext, notification)
    }

    fun play(context: Context) {
        if (!muted) {
            if (ringtone == null) {
                init(context)
            }
            ringtone?.play()
        }
    }

    fun setMuted(mute: Boolean) {
        muted = mute
    }

    fun isMuted(): Boolean = muted

    fun release() {
        ringtone?.stop()
        ringtone = null
    }
}
