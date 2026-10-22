package com.example.myapplication

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MedicineCheckActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val blue = Color.rgb(121, 189, 238)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply { isLenient = false }
    private val today get() = dateFmt.format(Calendar.getInstance().time)
    private lateinit var content: LinearLayout
    private var prescriptions = listOf<Pair<String, Map<String, Any>>>()
    private var checks = mapOf<String, Map<String, Any>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(246, 250, 255)) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(
            dp(20),
            dp(24),
            dp(20),
            dp(32)
        )
        }
        scroll.addView(content)
        setContentView(scroll)
        load()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                312
            )
        }
    }

    override fun onResume() {
        super.onResume(); if (::content.isInitialized) load()
    }

    private fun load() {
        val uid = auth.currentUser?.uid ?: run { toast("로그인이 필요해요."); return }
        db.collection("users").document(uid).collection("medicineRecords").get()
            .addOnSuccessListener { snap ->
                prescriptions = snap.documents.map { it.id to it.data.orEmpty() }
                    .filter { active(it.second, today) }
                db.collection("users").document(uid).collection("medicineIntakes")
                    .whereEqualTo("date", today).get()
                    .addOnSuccessListener { logs ->
                        checks = logs.documents.associate { it.id to it.data.orEmpty() }
                        render()
                    }.addOnFailureListener { toast("복약 체크 기록을 불러오지 못했어요.") }
            }.addOnFailureListener { toast("처방약 기록을 불러오지 못했어요.") }
    }

    private fun active(map: Map<String, Any>, date: String): Boolean {
        // 처방전 전체를 복용 완료로 변경하면 오늘의 복약에서 제외합니다.
        if (map["status"]?.toString()?.trim() == "복용 완료") return false
        return try {
            val start =
                dateFmt.parse(map["scheduleStartDate"]?.toString() ?: return false) ?: return false
            val days = map["prescriptionDays"]?.toString()?.toIntOrNull() ?: return false
            if (days !in 1..365) return false
            val end = Calendar.getInstance()
                .apply { time = start; add(Calendar.DAY_OF_MONTH, days - 1) }.time
            val target = dateFmt.parse(date) ?: return false
            !target.before(start) && !target.after(end)
        } catch (_: Exception) {
            false
        }
    }

    private fun medicines(map: Map<String, Any>): List<Map<*, *>> =
        (map["medicines"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()

    // 한 처방의 한 알림 시간을 한 번의 복약으로 기록합니다.
    private fun intakeId(id: String, time: String) = "${id}_${today}_${time}"

    private fun pillButton(textValue: String, filled: Boolean = false): Button = Button(this).apply {
        text = textValue
        isAllCaps = false
        textSize = 13f
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(Color.rgb(39, 88, 125))
        background = GradientDrawable().apply {
            setColor(if (filled) Color.rgb(184, 223, 255) else Color.rgb(220, 239, 255))
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun render() {
        content.removeAllViews()
        title("💊 오늘의 복약 관리", 23)
        line("$today · 처방전별 복용 여부를 기록하세요.", 13, Color.GRAY)
        val all = prescriptions.flatMap { (id, map) -> storedTimes(map).map { id to it } }
        val completed = all.count { (id, time) -> checks[intakeId(id, time)]?.get("status") == "taken" }
        title("오늘의 복약 현황  $completed / ${all.size}회", 18)
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).also {
            it.max = maxOf(1, all.size)
            it.progress = completed
            it.progressTintList = ColorStateList.valueOf(blue)
            content.addView(it)
        }
        if (prescriptions.isEmpty()) line("오늘 등록된 복약 일정이 없어요. 복용 시작일과 기간을 확인해 주세요.", 15)
        prescriptions.forEach { (id, map) ->
            val meds = medicines(map)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(16).toFloat()
                }
            }
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            addText(card, "처방약 복용 · ${meds.size}종", 18, Color.rgb(38, 59, 83), true)
            addText(card, "${map["prescriptionDays"] ?: "?"}일분 · 시간별 한 번씩 체크", 13, Color.GRAY)
            val times = storedTimes(map)
            if (times.isEmpty()) addText(card, "설정된 복약 시간이 없어요. 약봉투의 복용 지시를 확인해 주세요.", 14, Color.GRAY)
            times.forEach { time ->
                val status = checks[intakeId(id, time)]?.get("status")?.toString()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(246, 250, 255))
                        cornerRadius = dp(12).toFloat()
                    }
                }
                card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                addText(info, "⏰ $time", 16, Color.rgb(40, 91, 134), true)
                addText(info, when (status) {
                    "taken" -> "✓ 복용 완료"
                    "skipped" -> "– 건너뜀"
                    else -> "복용 전"
                }, 13, if (status == "taken") Color.rgb(22, 122, 97) else Color.GRAY)
                row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
                val button = pillButton(if (status == "taken") "기록 수정" else "복약 체크", status != "taken")
                row.addView(button, LinearLayout.LayoutParams(-2, dp(44)))
                button.setOnClickListener {
                    AlertDialog.Builder(this).setTitle("$time · 처방약 복약 기록")
                        .setItems(arrayOf("복용 완료", "건너뜀", "기록 취소")) { _, which ->
                            saveCheck(id, time, when (which) { 0 -> "taken"; 1 -> "skipped"; else -> "" })
                        }.show()
                }
            }
            val setup = pillButton("⏰ 복약 알림 시간 변경", true)
            card.addView(setup, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
            setup.setOnClickListener { editTimes(id, map) }
            val detail = pillButton("처방약 상세 보기")
            card.addView(detail, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
            detail.setOnClickListener {
                val details = meds.mapIndexed { index, med ->
                    "• ${med["medicineName"]?.toString().orEmpty().ifBlank { "약 ${index + 1}" }}"
                }.joinToString("\n").ifBlank { "약 이름 확인 필요" }
                AlertDialog.Builder(this).setTitle("처방약 상세")
                    .setMessage("$details\n\n복약 알림: ${times.joinToString(", ").ifBlank { "설정 없음" }}\n\n약마다 복용법이 다를 수 있으니 반드시 처방 지시를 확인하세요.")
                    .setPositiveButton("닫기", null).show()
            }
        }
        line("※ 복약 체크는 처방전의 해당 시간대 복용을 기록합니다. 약마다 복용 시간이 다르면 처방 지시를 확인해 주세요.", 12, Color.GRAY)
    }

    // 저장된 시간이 우선입니다. 기존 기록의 약별 시간도 읽되 임의 복용 횟수는 추가하지 않습니다.
    private fun storedTimes(map: Map<String, Any>): List<String> {
        val common = (map["reminderTimes"] as? List<*>)
            ?.mapNotNull { it?.toString() }.orEmpty().filter { validTime(it) }
        if (common.isNotEmpty()) return common.distinct().sorted()
        val perDrug = (map["medicines"] as? List<*>)
            ?.flatMap { item ->
                val med = item as? Map<*, *> ?: return@flatMap emptyList<String>()
                (med["reminderTimes"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
            }.orEmpty().filter { validTime(it) }
        return perDrug.distinct().sorted()
    }

    private fun validTime(time: String) =
        Regex("""(?:[01]\d|2[0-3]):[0-5]\d""").matches(time)

    private fun editTimes(id: String, map: Map<String, Any>) {
        val existing = storedTimes(map)
        // 횟수를 알 수 없으면 예시 3개를 보여주지만 기본으로 활성화하지 않습니다.
        val defaults = listOf("08:30", "13:30", "19:30")
        val selected = existing.toMutableList()
        val labels = listOf("☀️ 아침", "🍱 점심", "🌙 저녁")
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val help = TextView(this).apply {
            text = if (existing.isEmpty())
                "추천 시간을 표시했어요. 약봉투의 복용 횟수를 확인한 뒤 필요한 시간만 선택하세요."
            else "현재 저장된 시간이에요. 누르면 시간을 바꿀 수 있어요."
            textSize = 13f
            setTextColor(Color.rgb(70, 91, 115))
            setPadding(0, 0, 0, dp(12))
        }
        panel.addView(help)
        val rowCount = maxOf(3, existing.size)
        for (index in 0 until rowCount) {
            val initial = existing.getOrNull(index) ?: defaults.getOrElse(index) { "09:00" }
            var current = initial
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(241, 248, 255))
                    cornerRadius = dp(12).toFloat()
                }
            }
            val checkbox = CheckBox(this).apply {
                text = labels.getOrElse(index) { "알림 ${index + 1}" }
                textSize = 14f
                isChecked = index < existing.size
                buttonTintList = ColorStateList.valueOf(blue)
            }
            val timeButton = Button(this).apply {
                text = current
                isAllCaps = false
                setTextColor(Color.rgb(40, 91, 134))
                backgroundTintList = ColorStateList.valueOf(Color.rgb(220, 239, 255))
            }
            timeButton.setOnClickListener {
                val parts = current.split(":")
                TimePickerDialog(this, { _, hour, minute ->
                    current = String.format(Locale.KOREA, "%02d:%02d", hour, minute)
                    timeButton.text = current
                    checkbox.isChecked = true
                }, parts[0].toInt(), parts[1].toInt(), true).show()
            }
            checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    if (!selected.contains(current)) selected.add(current)
                } else selected.remove(current)
            }
            // 선택한 시간이 변경되었을 때에도 최신 시간으로 저장하도록 뷰에 상태 보관
            row.tag = Pair(checkbox, timeButton)
            row.addView(checkbox, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(timeButton)
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        AlertDialog.Builder(this)
            .setTitle("⏰ 복약 알림 시간")
            .setView(panel)
            .setNegativeButton("취소", null)
            .setNeutralButton("알림 끄기") { _, _ -> saveReminderTimes(id, map, emptyList()) }
            .setPositiveButton("이 시간으로 저장") { _, _ ->
                val chosen = (0 until panel.childCount).mapNotNull { i ->
                    val row = panel.getChildAt(i) as? LinearLayout ?: return@mapNotNull null
                    val pair = row.tag as? Pair<*, *> ?: return@mapNotNull null
                    val check = pair.first as? CheckBox ?: return@mapNotNull null
                    val button = pair.second as? Button ?: return@mapNotNull null
                    if (check.isChecked) button.text.toString() else null
                }.distinct().sorted()
                saveReminderTimes(id, map, chosen)
            }.show()
    }

    private fun saveReminderTimes(id: String, map: Map<String, Any>, times: List<String>) {
        if (!times.all { validTime(it) }) {
            toast("알림 시간 형식을 확인해 주세요.")
            return
        }
        val uid = auth.currentUser?.uid ?: return
        val updatedMedicines = medicines(map).map { med ->
            med.entries.associate { it.key.toString() to it.value } + ("reminderTimes" to times)
        }
        db.collection("users").document(uid).collection("medicineRecords").document(id)
            .update(mapOf("reminderTimes" to times, "medicines" to updatedMedicines))
            .addOnSuccessListener {
                MedicineAlarmScheduler.update(this, uid, id, map + ("reminderTimes" to times))
                toast(if (times.isEmpty()) "복약 알림을 껐어요." else "복약 알림 시간을 저장했어요.")
                load()
            }.addOnFailureListener { toast("저장 실패: ${it.localizedMessage}") }
    }

    private fun saveCheck(id: String, time: String, status: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("users").document(uid).collection("medicineIntakes")
            .document(intakeId(id, time))
        val task = if (status.isEmpty()) ref.delete() else ref.set(
            mapOf(
                "prescriptionId" to id, "date" to today, "time" to time,
                "status" to status, "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        task.addOnSuccessListener { load() }
            .addOnFailureListener { toast("기록 저장 실패: ${it.localizedMessage}") }
    }

    private fun title(value: String, size: Int) {
        addText(content, value, size, Color.rgb(32, 44, 67), true)
    }

    private fun line(value: String, size: Int, color: Int = Color.DKGRAY) {
        addText(content, value, size, color)
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Int,
        color: Int,
        bold: Boolean = false
    ) {
        val t = TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(color)
            setPadding(0, dp(7), 0, dp(7)); if (bold) setTypeface(null, Typeface.BOLD)
        }
        parent.addView(t)
    }

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
