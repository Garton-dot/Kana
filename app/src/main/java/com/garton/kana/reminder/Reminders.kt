package com.garton.kana.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.garton.kana.MainActivity
import com.garton.kana.R
import com.garton.kana.engine.Progression
import com.garton.kana.storage.ProgressRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Reminders {
    private const val CHANNEL = "streak"
    private const val REMINDER_HOUR = 19

    /** Schedules (or cancels) a daily evening nudge. Re-run on every app start. */
    fun schedule(context: Context, enabled: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.cancel(intent)
        if (!enabled) return
        var next = LocalDateTime.of(LocalDate.now(), LocalTime.of(REMINDER_HOUR, 0))
        if (next.isBefore(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, intent)
    }

    fun notifyIfNeeded(context: Context) {
        val state = ProgressRepository(context).load()
        val today = LocalDate.now().toEpochDay()
        if (!state.remindersOn || state.lastPracticeDay == today) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Streak reminders", NotificationManager.IMPORTANCE_DEFAULT))

        val streak = Progression.currentStreak(state, today)
        val (title, text) = if (streak > 0) {
            "🔥 Your $streak-day streak is at risk!" to "One quick lesson keeps it alive. がんばって!"
        } else {
            "あ… Time for kana!" to "Five minutes today beats an hour next week."
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.schedule(context, ProgressRepository(context).load().remindersOn)
        } else {
            Reminders.notifyIfNeeded(context)
        }
    }
}
