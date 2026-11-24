package pl.larpnet.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.net.toUri
import pl.larpnet.android.R

/**
 * The sound chat notifications play, picked in Settings -> Chat. The Larpnet sounds are in
 * `res/raw/` (Kenney's "Interface Sounds", CC0, some joined into two-note sounds), so chat
 * sounds different from everything else on the phone by default.
 *
 * Android fixes a channel's sound when the channel is created; an app can't change it later.
 * So each choice has its own chat channel ([channelId]) and switching deletes the others.
 * Deleting a channel takes its notifications down with it -- [PushNotifications] re-posts
 * them, silently, on the new one.
 */
enum class ChatSound(val key: String, val rawName: String?, @StringRes val labelRes: Int) {
    // The first entry is the default.
    DOUBLE_GLASS("double_glass", "chat_double_glass", R.string.chat_sound_double_glass),
    GLASS("glass", "chat_glass", R.string.chat_sound_glass),
    SHORT("short", "chat_short", R.string.chat_sound_short),
    RISING("rising", "chat_rising", R.string.chat_sound_rising),
    TWO_TONE("two_tone", "chat_two_tone", R.string.chat_sound_two_tone),
    QUESTION("question", "chat_question", R.string.chat_sound_question),
    BONG("bong", "chat_bong", R.string.chat_sound_bong),
    PLUCK("pluck", "chat_pluck", R.string.chat_sound_pluck),
    DOUBLE_BONG("double_bong", "chat_double_bong", R.string.chat_sound_double_bong),
    DROP_GLASS("drop_glass", "chat_drop_glass", R.string.chat_sound_drop_glass),

    /** The phone's notification sound -- also whatever the user set on the chat channel
     * themselves before this setting existed (see [ChatSounds.initialChoice]). */
    SYSTEM("system", null, R.string.chat_sound_system),
    SILENT("silent", null, R.string.chat_sound_silent),
    ;

    companion object {
        val DEFAULT = DOUBLE_GLASS
        fun fromKey(key: String?): ChatSound? = entries.firstOrNull { it.key == key }
    }
}

object ChatSounds {
    private const val PREFS_NAME = "larpnet_chat_sound"
    private const val KEY_SOUND = "sound"

    /** The chat channel from before sounds could be chosen. Kept as [ChatSound.SYSTEM]'s
     * channel, so whoever had tuned it in the phone's settings keeps their tuning. */
    private const val LEGACY_CHANNEL = "larpnet_chat"
    private const val CHANNEL_PREFIX = "larpnet_chat_"

    data class Channel(val id: String, val removedOthers: Boolean)

    fun channelId(sound: ChatSound): String =
        if (sound == ChatSound.SYSTEM) LEGACY_CHANNEL else CHANNEL_PREFIX + sound.key

    fun current(context: Context): ChatSound {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ChatSound.fromKey(prefs.getString(KEY_SOUND, null))?.let { return it }
        val initial = initialChoice(context)
        prefs.edit { putString(KEY_SOUND, initial.key) }
        return initial
    }

    fun select(context: Context, sound: ChatSound) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_SOUND, sound.key) }
    }

    /**
     * Makes sure the current choice's channel exists and no other chat channel does. Returns
     * the channel to post on, and whether another one was just deleted (its notifications went
     * with it).
     */
    fun ensureChannel(context: Context): Channel {
        val sound = current(context)
        val id = channelId(sound)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return Channel(id, false)
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(buildChannel(context, sound, id))
        }
        var removed = false
        for (other in ChatSound.entries.map(::channelId)) {
            if (other != id && manager.getNotificationChannel(other) != null) {
                manager.deleteNotificationChannel(other)
                removed = true
            }
        }
        return Channel(id, removed)
    }

    /** Plays [sound] once, for the picker. Returns the playing ringtone so the caller can stop
     * it when another one is picked. */
    fun preview(context: Context, sound: ChatSound): Ringtone? {
        val uri = soundUri(context, sound) ?: return null
        return RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = audioAttributes()
            play()
        }
    }

    /**
     * Existing installs: anyone who changed the old chat channel's sound or importance in the
     * phone's settings stays on it ([ChatSound.SYSTEM]); everyone else gets the Larpnet sound.
     */
    private fun initialChoice(context: Context): ChatSound {
        val legacy = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(LEGACY_CHANNEL)
            ?: return ChatSound.DEFAULT
        val customized = legacy.importance != NotificationManager.IMPORTANCE_HIGH ||
            legacy.sound != Settings.System.DEFAULT_NOTIFICATION_URI ||
            (Build.VERSION.SDK_INT >= 30 && legacy.hasUserSetSound())
        return if (customized) ChatSound.SYSTEM else ChatSound.DEFAULT
    }

    private fun buildChannel(context: Context, sound: ChatSound, id: String): NotificationChannel =
        NotificationChannel(id, context.getString(R.string.push_channel_chat), NotificationManager.IMPORTANCE_HIGH).apply {
            when (sound) {
                ChatSound.SYSTEM -> Unit // the platform default sound
                ChatSound.SILENT -> setSound(null, null)
                else -> setSound(soundUri(context, sound), audioAttributes())
            }
        }

    /** By resource *name*, not id: the channel stores this URI, and ids can change between
     * builds. */
    private fun soundUri(context: Context, sound: ChatSound): Uri? = when {
        sound == ChatSound.SILENT -> null
        sound.rawName == null -> Settings.System.DEFAULT_NOTIFICATION_URI
        else -> "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/${sound.rawName}".toUri()
    }

    private fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}
