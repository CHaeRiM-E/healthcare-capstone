package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply { isLenient = false }
    private val today: String get() = dateFormat.format(Calendar.getInstance().time)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        fun open(target: Class<*>) = startActivity(Intent(this, target))
        findViewById<View>(R.id.btnHealthBenefits).setOnClickListener { open(HealthBenefitsActivity::class.java) }
        findViewById<View>(R.id.btnCardNews).setOnClickListener { open(HealthCardNewsActivity::class.java) }
        findViewById<View>(R.id.btnMedicine).setOnClickListener { open(MedicineActivity::class.java) }
        findViewById<View>(R.id.btnInternalChat).setOnClickListener { open(InternalChatActivity::class.java) }
        findViewById<View>(R.id.btnInternalRecord).setOnClickListener { open(InternalRecordActivity::class.java) }
        findViewById<View>(R.id.btnInjuryRecord).setOnClickListener { open(InjuryRecordActivity::class.java) }
        findViewById<View>(R.id.btnCameraResult).setOnClickListener { open(CameraActivity::class.java) }
        findViewById<View>(R.id.btnMyInfo).setOnClickListener { open(MyInfoActivity::class.java) }
        findViewById<View>(R.id.btnHospital).setOnClickListener { open(HospitalActivity::class.java) }
        findViewById<View>(R.id.navHome).setOnClickListener { Toast.makeText(this, "현재 홈 화면입니다.", Toast.LENGTH_SHORT).show() }
        findViewById<View>(R.id.navAi).setOnClickListener { open(InternalChatActivity::class.java) }
        findViewById<View>(R.id.navSchedule).setOnClickListener { open(HealthCalendarActivity::class.java) }
        findViewById<View>(R.id.navNews).setOnClickListener { open(HealthCardNewsActivity::class.java) }
        findViewById<View>(R.id.dashboardMedicine).setOnClickListener { open(MedicineCheckActivity::class.java) }
        findViewById<View>(R.id.dashboardVisit).setOnClickListener { open(HealthCalendarActivity::class.java) }
        findViewById<View>(R.id.dashboardAlerts).setOnClickListener { open(MedicineCheckActivity::class.java) }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun isActive(record: Map<String, Any>, day: String): Boolean {
        if (record["status"]?.toString()?.trim() == "복용 완료") return false
        return try {
            val start = dateFormat.parse(record["scheduleStartDate"]?.toString() ?: return false) ?: return false
            val days = record["prescriptionDays"]?.toString()?.toIntOrNull() ?: return false
            if (days !in 1..365) return false
            val end = Calendar.getInstance().apply { time = start; add(Calendar.DAY_OF_MONTH, days - 1) }.time
            val target = dateFormat.parse(day) ?: return false
            !target.before(start) && !target.after(end)
        } catch (_: Exception) { false }
    }

    // 오늘의 복약 화면과 동일하게 공통 알림시간 우선, 없으면 약별 시간 사용.
    private fun storedTimes(record: Map<String, Any>): List<String> {
        val valid = Regex("""(?:[01]\d|2[0-3]):[0-5]\d""")
        val common = (record["reminderTimes"] as? List<*>)?.mapNotNull { it?.toString() }
            .orEmpty().filter { valid.matches(it) }
        if (common.isNotEmpty()) return common.distinct().sorted()
        val perDrug = (record["medicines"] as? List<*>)?.flatMap { raw ->
            val med = raw as? Map<*, *> ?: return@flatMap emptyList<String>()
            (med["reminderTimes"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        }.orEmpty().filter { valid.matches(it) }
        return perDrug.distinct().sorted()
    }

    private fun loadDashboard() {
        val progress = findViewById<ProgressBar>(R.id.dashboardProgress)
        val summary = findViewById<TextView>(R.id.dashboardMedicineSummary)
        val details = findViewById<TextView>(R.id.dashboardMedicineDetail)
        val visit = findViewById<TextView>(R.id.dashboardVisitText)
        val alerts = findViewById<TextView>(R.id.dashboardAlertsText)
        progress.max = 1; progress.progress = 0
        summary.text = "복약 정보 확인 중"
        details.text = "잠시만 기다려 주세요"
        visit.text = "예약 확인 중"
        alerts.text = "알림 확인 중"
        val uid = auth.currentUser?.uid ?: run {
            summary.text = "로그인이 필요해요"
            details.text = "로그인 후 복약 정보를 확인할 수 있어요"
            visit.text = "로그인 필요"; alerts.text = "로그인 필요"
            return
        }
        val day = today
        db.collection("users").document(uid).collection("medicineRecords").get()
            .addOnSuccessListener { snapshot ->
                val active = snapshot.documents.map { it.id to it.data.orEmpty() }
                    .filter { isActive(it.second, day) }
                val slots = active.flatMap { (id, map) -> storedTimes(map).map { time -> "${id}_${day}_${time}" } }
                val total = slots.size
                alerts.text = if (total == 0) "오늘 설정된 복약 알림 없음" else "오늘 복약 시간 ${total}건 설정"
                if (total == 0) {
                    summary.text = "0 / 0회 완료"
                    details.text = if (active.isEmpty()) "오늘 복용할 처방약이 없어요" else "복약 시간을 설정해 주세요"
                    progress.max = 1; progress.progress = 0
                } else {
                    db.collection("users").document(uid).collection("medicineIntakes")
                        .whereEqualTo("date", day).get().addOnSuccessListener { logs ->
                            val checked = logs.documents.associate { it.id to it.data.orEmpty() }
                            val completed = slots.count { checked[it]?.get("status") == "taken" }
                            summary.text = "$completed / $total 회 완료"
                            details.text = "남은 복약 일정 ${total - completed}회 · 눌러서 체크하기"
                            progress.max = total; progress.progress = completed
                        }.addOnFailureListener {
                            summary.text = "복약 체크 조회 실패"
                            details.text = "화면을 다시 열어 주세요"
                        }
                }
            }.addOnFailureListener {
                summary.text = "처방약 조회 실패"
                details.text = "네트워크 연결을 확인해 주세요"
                alerts.text = "알림 조회 실패"
            }
        db.collection("users").document(uid).collection("visitRecords")
            .whereEqualTo("recordType", "booking").get().addOnSuccessListener { snapshot ->
                val next = snapshot.documents.mapNotNull { doc ->
                    val map = doc.data ?: return@mapNotNull null
                    val date = map["visitDate"]?.toString() ?: return@mapNotNull null
                    if (date < day) null else map
                }.minByOrNull { it["visitDate"].toString() }
                visit.text = if (next == null) "예정된 진료 예약 없음" else {
                    val hospital = next["hospitalName"]?.toString().orEmpty()
                    "${next["visitDate"]} $hospital".trim()
                }
            }.addOnFailureListener { visit.text = "예약 조회 실패" }
    }
}