package com.example.myapplication

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class MedicineRecordActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val statusOptions = arrayOf("확인 필요", "복용 중", "복용 완료")

    // Keep Android alarm registrations in sync with prescription-level status.
    private fun syncPrescriptionAlarms(uid: String, id: String, record: Map<String, Any>?, active: Boolean) {
        val prefs = getSharedPreferences("medicine_alarm_schedule", MODE_PRIVATE)
        val key = "$uid:$id"
        val oldIds = try {
            org.json.JSONArray(prefs.getString(key, "[]") ?: "[]")
        } catch (_: Exception) { org.json.JSONArray() }
        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        for (i in 0 until oldIds.length()) {
            val alarmKey = oldIds.optString(i)
            val intent = android.content.Intent(this, MedicineAlarmReceiver::class.java).apply {
                data = android.net.Uri.parse("medalarm://${android.net.Uri.encode(alarmKey)}")
            }
            val pending = android.app.PendingIntent.getBroadcast(
                this, 0, intent,
                android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
        prefs.edit().remove(key).remove("meta:$key").apply()
        if (active && record != null) {
            MedicineAlarmScheduler.update(this, uid, id, record)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine_record)

        val recordLayout = findViewById<LinearLayout>(R.id.layoutMedicineRecords)
        val emptyText = findViewById<TextView>(R.id.txtMedicineRecordEmpty)
        findViewById<Button>(R.id.btnMedicineRecordBack).setOnClickListener { finish() }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            emptyText.text = "로그인이 필요합니다."
            return
        }

        emptyText.visibility = View.VISIBLE
        emptyText.text = "처방약 기록을 불러오는 중..."
        val collection = db.collection("users").document(user.uid)
            .collection("medicineRecords")

        collection.orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                // Remove old cards, but keep the empty-state TextView.
                for (i in recordLayout.childCount - 1 downTo 0) {
                    if (recordLayout.getChildAt(i) !== emptyText) {
                        recordLayout.removeViewAt(i)
                    }
                }
                // Empty label is the first child; cards follow it.
                if (documents.isEmpty) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "저장된 처방약 기록이 없습니다."
                    return@addOnSuccessListener
                }
                emptyText.visibility = View.GONE

                for (document in documents) {
                    val recordRef = collection.document(document.id)
                    val prescriptionDate = document.getString("prescriptionDate")
                        ?.takeIf { it.isNotBlank() } ?: "날짜 미확인"
                    val prescriptionDays = document.get("prescriptionDays")
                        ?.toString()?.takeIf { it.isNotBlank() } ?: "미확인"
                    val savedDate = document.getTimestamp("createdAt")?.toDate()?.let {
                        SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(it)
                    } ?: "날짜 미확인"
                    val cardTitle = "💊 $savedDate 처방약 기록"
                    var overallStatus = document.getString("status")
                        ?.takeIf { it.isNotBlank() } ?: "확인 필요"

                    // Keep a local copy for immediate display. Firestore transaction
                    // refreshes the latest server data before updating one medicine.
                    val medicineItems = ((document.get("medicines") as? List<*>) ?: emptyList<Any>())
                        .mapNotNull { raw ->
                            (raw as? Map<*, *>)?.entries?.associate { entry ->
                                entry.key.toString() to entry.value
                            }?.toMutableMap()
                        }.toMutableList()

                    val medicineCard = LinearLayout(this@MedicineRecordActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(20, 20, 20, 20)
                        background = GradientDrawable().apply {
                            setColor(Color.WHITE)
                            cornerRadius = 18f
                        }
                        elevation = 3f
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 20 }
                    }

                    val recordText = TextView(this@MedicineRecordActivity).apply {
                        textSize = 15f
                        setTextColor(Color.parseColor("#202940"))
                        setLineSpacing(6f, 1f)
                        setPadding(24, 24, 24, 24)
                    }

                    fun renderCard() {
                        val names = if (medicineItems.isEmpty()) {
                            "등록된 의약품이 없습니다."
                        } else {
                            medicineItems.joinToString("\n") { item ->
                                val name = item["medicineName"] as? String ?: "이름 미확인"
                                val status = (item["status"] as? String)
                                    ?.takeIf { it.isNotBlank() } ?: "확인 필요"
                                "• $name  |  $status"
                            }
                        }
                        recordText.text = "$cardTitle\n\n" +
                                "📅 처방 날짜: $prescriptionDate\n" +
                                "⏱ 복용 일수: $prescriptionDays\n" +
                                "💜 복용 상태: $overallStatus\n\n" + names
                    }
                    renderCard()
                    medicineCard.addView(recordText)

                    val medicineStatusButton = Button(this@MedicineRecordActivity).apply {
                        text = "💊 약별 복용 상태 변경"
                        textSize = 14f
                        isAllCaps = false
                        setTextColor(Color.parseColor("#6267E9"))
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEEEFF"))
                        setOnClickListener {
                            if (medicineItems.isEmpty()) {
                                Toast.makeText(this@MedicineRecordActivity,
                                    "변경할 의약품이 없습니다.", Toast.LENGTH_SHORT).show()
                            } else {
                                val names = medicineItems.map {
                                    it["medicineName"] as? String ?: "이름 미확인"
                                }
                                AlertDialog.Builder(this@MedicineRecordActivity)
                                    .setTitle("상태를 변경할 약 선택")
                                    .setItems(names.toTypedArray()) { _, which ->
                                        val selectedName = names[which]
                                        AlertDialog.Builder(this@MedicineRecordActivity)
                                            .setTitle("$selectedName 복용 상태")
                                            .setItems(statusOptions) { _, statusIndex ->
                                                val selectedStatus = statusOptions[statusIndex]
                                                // Transaction avoids overwriting changes from
                                                // another device to unrelated medicine entries.
                                                db.runTransaction { transaction ->
                                                    val latest = transaction.get(recordRef)
                                                    val latestMedicines = latest.get("medicines") as? List<*>
                                                        ?: emptyList<Any>()
                                                    val updated = latestMedicines.mapIndexed { index, raw ->
                                                        val item = (raw as? Map<*, *>)
                                                            ?.entries?.associate { entry ->
                                                                entry.key.toString() to entry.value
                                                            }?.toMutableMap() ?: mutableMapOf<String, Any?>()
                                                        if (index == which) {
                                                            item["status"] = selectedStatus
                                                        }
                                                        item
                                                    }
                                                    transaction.update(recordRef, "medicines", updated)
                                                    updated
                                                }.addOnSuccessListener { updated ->
                                                    medicineItems.clear()
                                                    medicineItems.addAll(updated.map { it.toMutableMap() })
                                                    renderCard()
                                                    Toast.makeText(this@MedicineRecordActivity,
                                                        "$selectedName: $selectedStatus 저장 완료",
                                                        Toast.LENGTH_SHORT).show()
                                                }.addOnFailureListener { e ->
                                                    Toast.makeText(this@MedicineRecordActivity,
                                                        "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            .setNegativeButton("취소", null)
                                            .show()
                                    }
                                    .setNegativeButton("취소", null)
                                    .show() // Required: display the FIRST medicine picker.
                            }
                        }
                    }
                    medicineCard.addView(medicineStatusButton)

                    val statusButton = Button(this@MedicineRecordActivity).apply {
                        text = "💜 복용 상태 변경"
                        textSize = 14f
                        isAllCaps = false
                        setTextColor(Color.parseColor("#6267E9"))
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEEEFF"))
                        setOnClickListener {
                            AlertDialog.Builder(this@MedicineRecordActivity)
                                .setTitle("처방전 전체 복용 상태 변경")
                                .setItems(statusOptions) { _, which ->
                                    val selectedStatus = statusOptions[which]
                                    recordRef.update("status", selectedStatus)
                                        .addOnSuccessListener {
                                            overallStatus = selectedStatus
                                            syncPrescriptionAlarms(user.uid, document.id, document.data, selectedStatus != "복용 완료")
                                            renderCard()
                                            Toast.makeText(this@MedicineRecordActivity,
                                                "복용 상태를 변경했어요.", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(this@MedicineRecordActivity,
                                                "변경 실패: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                }
                                .setNegativeButton("취소", null)
                                .show()
                        }
                    }
                    medicineCard.addView(statusButton)

                    val deleteButton = Button(this@MedicineRecordActivity).apply {
                        text = "🗑 기록 삭제"
                        textSize = 14f
                        isAllCaps = false
                        setTextColor(Color.parseColor("#D94A4A"))
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF0F0"))
                        setOnClickListener {
                            AlertDialog.Builder(this@MedicineRecordActivity)
                                .setTitle("처방약 기록 삭제")
                                .setMessage("이 처방약 기록을 삭제할까요?\n삭제한 기록은 복구할 수 없습니다.")
                                .setNegativeButton("취소", null)
                                .setPositiveButton("삭제") { _, _ ->
                                    recordRef.delete()
                                        .addOnSuccessListener {
                                            syncPrescriptionAlarms(user.uid, document.id, null, false)
                                            recordLayout.removeView(medicineCard)
                                            if (recordLayout.childCount == 1) {
                                                emptyText.visibility = View.VISIBLE
                                                emptyText.text = "저장된 처방약 기록이 없습니다."
                                            }
                                            Toast.makeText(this@MedicineRecordActivity,
                                                "처방약 기록을 삭제했어요.", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(this@MedicineRecordActivity,
                                                "삭제 실패: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                }
                                .show()
                        }
                    }
                    medicineCard.addView(deleteButton)
                    recordLayout.addView(medicineCard)
                }
            }
            .addOnFailureListener { e ->
                emptyText.visibility = View.VISIBLE
                emptyText.text = "기록을 불러오지 못했습니다."
                Toast.makeText(this, "불러오기 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

}
