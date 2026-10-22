package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

object VisitReminderScheduler {
    private const val PREFS = "visit_reminder_cache"
    private const val KEY = "scheduled"
    private const val ACTION = "com.example.myapplication.VISIT_REMINDER"

    fun sync(context: Context, uid: String, records: List<Pair<String, Map<String, Any>>>) {
        if (uid.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = JSONArray(prefs.getString(KEY, "[]"))
        for (i in 0 until old.length()) {
            val item = old.getJSONObject(i)
            cancel(context, item.optString("key"))
        }
        val items = JSONArray()
        for ((id, record) in records) {
            if (record["recordType"] != "booking") continue
            val date = record["visitDate"]?.toString().orEmpty()
            val time = record["visitTime"]?.toString().orEmpty()
            val appointment = try {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).apply { isLenient = false }
                    .parse("$date $time")?.time ?: continue
            } catch (_: Exception) { continue }
            val hospital = record["hospitalName"]?.toString().orEmpty()
            for ((enabledField, offset, label) in listOf(
                Triple("remindDay", 24L * 60 * 60 * 1000, "내일"),
                Triple("remindHour", 60L * 60 * 1000, "1시간 후")
            )) {
                if (record[enabledField] != true) continue
                val trigger = appointment - offset
                if (trigger <= System.currentTimeMillis()) continue
                val key = "$uid:$id:$enabledField"
                val item = JSONObject().apply {
                    put("key", key)
                    put("uid", uid)
                    put("trigger", trigger)
                    put("hospital", hospital)
                    put("date", date)
                    put("time", time)
                    put("label", label)
                }
                items.put(item)
                schedule(context, item)
            }
        }
        prefs.edit().putString(KEY, items.toString()).apply()
    }

    fun restore(context: Context) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = JSONArray(prefs.getString(KEY, "[]"))
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("uid") == currentUid && item.optLong("trigger") > System.currentTimeMillis()) {
                schedule(context, item)
            }
        }
    }

    private fun schedule(context: Context, item: JSONObject) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, item.optString("key"), item)
        val trigger = item.optLong("trigger")
        // Inexact alarms: Android may delay delivery for battery optimization.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }


    fun testNotification(context: Context) {
        val uid = com.google.firebase.auth.FirebaseAuth
            .getInstance().currentUser?.uid ?: return

        val intent = Intent(context, VisitReminderReceiver::class.java).apply {
            putExtra("uid", uid)
            putExtra("hospital", "알림 테스트 병원")
            putExtra("date", "테스트")
            putExtra("time", "1분 뒤")
            putExtra("label", "테스트 알림")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            98765,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 60_000L,
            pendingIntent
        )
    }


    private fun cancel(context: Context, key: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, key, null))
    }

    private fun pendingIntent(context: Context, key: String, item: JSONObject?): PendingIntent {
        val intent = Intent(context, VisitReminderReceiver::class.java).apply {
            action = ACTION
            data = android.net.Uri.parse("visitreminder://alarm/${android.net.Uri.encode(key)}")
            if (item != null) {
                putExtra("uid", item.optString("uid"))
                putExtra("hospital", item.optString("hospital"))
                putExtra("date", item.optString("date"))
                putExtra("time", item.optString("time"))
                putExtra("label", item.optString("label"))
                putExtra("key", key)
            }
        }
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class VisitReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            VisitReminderScheduler.restore(context)
        }
    }
}
