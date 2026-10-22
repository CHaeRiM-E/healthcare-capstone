package com.example.myapplication

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object MedicineAlarmScheduler {
    private const val PREF = "medicine_alarm_schedule"
    private const val CHANNEL = "medicine_reminder_channel"

    fun update(context: Context, uid: String, id: String, map: Map<String, Any>) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val key = "$uid:$id"
        val old = JSONArray(prefs.getString(key, "[]") ?: "[]")
        for (i in 0 until old.length()) cancel(context, old.optString(i))
        val newIds = JSONArray()
        val startString = map["scheduleStartDate"]?.toString().orEmpty()
        val start = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply { isLenient = false }
                .parse(startString)
        } catch (_: Exception) {
            null
        }
        val days = map["prescriptionDays"]?.toString()?.toIntOrNull() ?: 0
        val times = (map["reminderTimes"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        if (start != null && days in 1..365) {
            val cal = Calendar.getInstance().apply { time = start }
            for (d in 0 until days) {
                for (time in times) {
                    val parts = time.split(":").mapNotNull { it.toIntOrNull() }
                    if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) continue
                    val at = (cal.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, parts[0]); set(Calendar.MINUTE, parts[1])
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }
                    val delta = at.timeInMillis - System.currentTimeMillis()
                    if (delta <= 0 || delta > 14L * 24 * 60 * 60 * 1000) continue
                    val alarmKey =
                        "$key:${SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)}:$time"
                    val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
                        data = Uri.parse("medalarm://${Uri.encode(alarmKey)}")
                        putExtra("uid", uid); putExtra("alarmKey", alarmKey)
                    }
                    val pending = PendingIntent.getBroadcast(
                        context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, at.timeInMillis, pending
                    ) else am.set(AlarmManager.RTC_WAKEUP, at.timeInMillis, pending)
                    newIds.put(alarmKey)
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val metadata = JSONObject().apply {
            put("start", startString); put("days", days)
            put("times", JSONArray(times)); put("uid", uid); put("id", id)
        }
        prefs.edit().putString(key, newIds.toString())
            .putString("meta:$key", metadata.toString()).apply()
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val saved = prefs.all.filterKeys { it.startsWith("meta:$uid:") }
        saved.values.forEach { value ->
            try {
                val data = JSONObject(value as String)
                val id = data.getString("id")
                val times = data.getJSONArray("times")
                update(
                    context, uid, id, mapOf(
                    "scheduleStartDate" to data.getString("start"),
                    "prescriptionDays" to data.getInt("days").toString(),
                    "reminderTimes" to (0 until times.length()).map { times.getString(it) }
                ))
            } catch (_: Exception) { /* ignore malformed local metadata */
            }
        }
    }

    private fun cancel(context: Context, key: String) {
        val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
            data = Uri.parse("medalarm://${Uri.encode(key)}")
        }
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL, "복약 알림", NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    fun notify(context: Context, key: String) {
        createChannel(context)
        val intent = Intent(context, MedicineCheckActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💊 복약 시간이에요")
            .setContentText("약봉투의 복용 지시를 확인하고 복약 여부를 기록해 주세요.")
            .setContentIntent(pending).setAutoCancel(true).build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(key.hashCode(), note)
        }
    }
}

class MedicineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getStringExtra("uid") ?: return
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        MedicineAlarmScheduler.notify(context, intent.getStringExtra("alarmKey") ?: "medicine")
    }
}

class MedicineBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            MedicineAlarmScheduler.restore(context)
        }
    }
}
