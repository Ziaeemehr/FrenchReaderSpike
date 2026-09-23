package com.ziaee.frenchreader.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ziaee.frenchreader.MainActivity
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.reviewableCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneId

object VocabReviewReminder {
    private const val REQUEST_CODE = 4701

    fun schedule(context: Context) {
        cancel(context)
        if (!VocabPrefs.getReminderEnabled(context)) return
        val alarm = context.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        var next = now.withHour(VocabPrefs.getReminderHour(context))
            .withMinute(VocabPrefs.getReminderMinute(context)).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context)
        )
    }

    fun cancel(context: Context) =
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, VocabReviewReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class VocabReviewBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) VocabReviewReminder.schedule(context)
    }
}

class VocabReviewReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                val remainingNew = (VocabPrefs.getMaxNewCards(app) - VocabPrefs.getNewReviewedToday(app, date)).coerceAtLeast(0)
                val hasWork = reviewableCount(AppDatabase.get(app).vocabDao().getAllOnce(), now, remainingNew) > 0
                if (hasWork) notifyDue(app)
            } finally { pending.finish() }
        }
    }

    private fun notifyDue(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "vocabulary_review"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, context.getString(R.string.review_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            4701,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.review_reminder_title))
                .setContentText(context.getString(R.string.review_reminder_body))
                .setContentIntent(open).setAutoCancel(true).build()
        )
    }
}
