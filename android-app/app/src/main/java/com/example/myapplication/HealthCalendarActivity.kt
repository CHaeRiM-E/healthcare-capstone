package com.example.myapplication

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HealthCalendarActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val ink = Color.rgb(32, 44, 67)
    private val muted = Color.rgb(104, 119, 146)
    private val purple = Color.rgb(58, 134, 255)
    private val mint = Color.rgb(54, 139, 118)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    private var selectedDate = dateFormat.format(Calendar.getInstance().time)
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var heading: TextView
    private lateinit var calendarGrid: LinearLayout
    private lateinit var monthLabel: TextView
    private val visibleMonth = Calendar.getInstance()
    private var records = listOf<Pair<String, Map<String, Any>>>()
    private var medicineRecords = listOf<Pair<String, Map<String, Any>>>()
    private val medicineBlue = Color.rgb(58, 134, 255)

    private var hospitalName = ""
    private var hospitalAddress = ""
    private var hospitalPhone = ""
    private var hospitalCategory = ""
    private var hospitalLat = 0.0
    private var hospitalLng = 0.0
    private var hospitalLabel: TextView? = null
    private var departmentField: EditText? = null

    private val picker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { data ->
                    hospitalName = data.getStringExtra("hospitalName").orEmpty()
                    hospitalAddress = data.getStringExtra("hospitalAddress").orEmpty()
                    hospitalPhone = data.getStringExtra("hospitalPhone").orEmpty()
                    hospitalCategory = data.getStringExtra("hospitalCategory").orEmpty()
                    hospitalLat = data.getDoubleExtra("hospitalLatitude", 0.0)
                    hospitalLng = data.getDoubleExtra("hospitalLongitude", 0.0)
                    hospitalLabel?.text =
                        if (hospitalName.isBlank()) "병원을 선택해 주세요" else "$hospitalName\n$hospitalAddress"
                    departmentField?.setText(guessDepartment(hospitalCategory, hospitalName))
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_calendar)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(root)
        findViewById<android.widget.FrameLayout>(R.id.healthCalendarRoot).addView(scroll)
        button(root, "← 뒤로", false) { finish() }
        title(root, "나의 건강 캘린더", 25)
        label(root, "진료 기록과 앞으로의 예약을 한눈에", 14, muted)
        val calendarCard = card(root)
        val monthNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        calendarCard.addView(monthNavigation)
        val prev = TextView(this).apply {
            text = "‹"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(ink)
            setOnClickListener { visibleMonth.add(Calendar.MONTH, -1); renderCalendar() }
        }
        monthNavigation.addView(prev, LinearLayout.LayoutParams(dp(48), dp(48)))
        monthLabel = TextView(this).apply {
            textSize = 19f; gravity = Gravity.CENTER; setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
        }
        monthNavigation.addView(monthLabel, LinearLayout.LayoutParams(0, dp(48), 1f))
        val next = TextView(this).apply {
            text = "›"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(ink)
            setOnClickListener { visibleMonth.add(Calendar.MONTH, 1); renderCalendar() }
        }
        monthNavigation.addView(next, LinearLayout.LayoutParams(dp(48), dp(48)))
        calendarGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        calendarCard.addView(calendarGrid)
        val legend = LinearLayout(this).apply {
            gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL
        }
        calendarCard.addView(legend)
        label(legend, "● 진료 완료", 13, purple)
        label(legend, "     ● 예약 예정", 13, mint)
        label(legend, "     ● 복약", 13, medicineBlue)
        heading = title(root, "", 18)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        button(root, "+ 진료 기록 또는 병원 예약 등록", true) { openEditor() }

        button(root, "🔔 알림 테스트 (1분 뒤)", false) {
            VisitReminderScheduler.testNotification(this)
            toast("테스트 알림을 예약했어요.")
        }

        button(root, "💊 오늘의 복약 체크·알림", false) {
            startActivity(Intent(this, MedicineCheckActivity::class.java))
        }
        renderCalendar()
        renderDay()
        loadRecords()
        loadMedicineRecords()
        requestNotificationPermission()
        if (intent.getBooleanExtra("fromBenefitRecommendation", false)) {
            hospitalName = intent.getStringExtra("hospitalName").orEmpty()
            hospitalAddress = intent.getStringExtra("hospitalAddress").orEmpty()
            hospitalPhone = intent.getStringExtra("hospitalPhone").orEmpty()
            hospitalCategory = intent.getStringExtra("hospitalCategory").orEmpty()
            hospitalLat = intent.getDoubleExtra("hospitalLatitude", 0.0)
            hospitalLng = intent.getDoubleExtra("hospitalLongitude", 0.0)
            openEditor(prefillFromBenefit = true)
        }
    }

    private fun renderDay() {
        heading.text = "$selectedDate 일정"
        list.removeAllViews()
        val matching = records.filter { (_, map) -> map["visitDate"] == selectedDate }
        val medications = medicineRecords.filter { (_, map) -> medicineOnDate(map, selectedDate) }
        if (matching.isEmpty() && medications.isEmpty()) {
            val c = card(list)
            label(c, "이 날짜에는 등록된 일정이 없어요.", 15, muted)
        } else {
            matching.forEach { (id, map) ->
                val c = card(list)
                val booked = map["recordType"] == "booking"
                label(c, if (booked) "● 예약 예정" else "● 진료 완료", 13, if (booked) mint else purple)
                title(c, map["hospitalName"].toString(), 17)
                val dep = map["department"].toString().takeUnless { it == "null" }.orEmpty()
                val symptoms = map["symptoms"].toString().takeUnless { it == "null" }.orEmpty()
                val time = map["visitTime"].toString().takeUnless { it == "null" }.orEmpty()
                label(
                    c,
                    listOf(dep, symptoms, time).filter { it.isNotBlank() }.joinToString(" · "),
                    14,
                    muted
                )
                c.setOnClickListener { showDetails(id, map) }
            }
        }
        medications.forEach { (prescriptionId, map) ->
            val names = (map["medicines"] as? List<*>)?.mapNotNull { raw ->
                (raw as? Map<*, *>)?.get("medicineName")?.toString()
            }?.filter { it.isNotBlank() }?.distinct().orEmpty()
            val medicineCard = card(list)
            label(medicineCard, "● 복약 일정", 13, medicineBlue)
            title(medicineCard, "💊 처방약 복용 · ${names.size}종", 16)
            label(medicineCard, "${map["prescriptionDays"] ?: "?"}일분 · 자세히 보려면 누르세요", 13, muted)
            val times = (map["reminderTimes"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
            val progressLabel = label(
                medicineCard,
                if (times.isEmpty()) "복약 시간 미설정" else "복약 완료 기록 확인 중…",
                13, medicineBlue
            )
            if (times.isNotEmpty()) {
                val uid = auth.currentUser?.uid
                val shownDate = selectedDate
                if (uid != null) {
                    db.collection("users").document(uid).collection("medicineIntakes")
                        .whereEqualTo("date", shownDate).get()
                        .addOnSuccessListener { logs ->
                            if (selectedDate != shownDate) return@addOnSuccessListener
                            val statuses =
                                logs.documents.associate { it.id to it.getString("status") }
                            val completed = times.count { time ->
                                statuses["${prescriptionId}_${shownDate}_${time}"] == "taken"
                            }
                            val skipped = times.count { time ->
                                statuses["${prescriptionId}_${shownDate}_${time}"] == "skipped"
                            }
                            progressLabel.text = "복약 완료 $completed / ${times.size}회" +
                                    if (skipped > 0) " · 건너뜀 ${skipped}회" else ""
                        }
                        .addOnFailureListener { progressLabel.text = "복약 기록을 불러오지 못했어요" }
                }
            }
            medicineCard.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("💊 처방약 상세")
                    .setMessage(
                        "복용 시작일: ${map["scheduleStartDate"] ?: "미확인"}\n" +
                                "복용 기간: ${map["prescriptionDays"] ?: "미확인"}일\n\n" +
                                "약 목록\n" + (if (names.isEmpty()) "약 이름 확인 필요" else names.joinToString(
                            "\n"
                        ) { "• $it" }) +
                                "\n\n약 이름과 복용 방법은 약봉투 및 약사 안내를 확인하세요."
                    )
                    .setPositiveButton("확인", null)
                    .setNeutralButton("복약 체크·알림") { _, _ ->
                        startActivity(Intent(this, MedicineCheckActivity::class.java))
                    }
                    .show()
            }
        }
    }

    private fun loadRecords() {
        val uid = auth.currentUser?.uid ?: run { toast("로그인이 필요해요."); return }
        db.collection("users").document(uid).collection("visitRecords")
            .orderBy("visitDate", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { snapshot ->
                records = snapshot.documents.map { it.id to it.data.orEmpty() }
                renderCalendar()
                renderDay()
                VisitReminderScheduler.sync(this, auth.currentUser?.uid.orEmpty(), records)
            }.addOnFailureListener { toast("기록을 불러오지 못했어요: ${it.localizedMessage}") }
    }

    override fun onResume() {
        super.onResume()
        if (::calendarGrid.isInitialized) loadMedicineRecords()
    }

    private fun loadMedicineRecords() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("medicineRecords")
            .get().addOnSuccessListener { snapshot ->
                medicineRecords = snapshot.documents.map { it.id to it.data.orEmpty() }
                renderCalendar()
                renderDay()
            }.addOnFailureListener {
                toast("복약 일정을 불러오지 못했어요: ${it.localizedMessage}")
            }
    }

    private fun medicineOnDate(record: Map<String, Any>, date: String): Boolean {
        // Completed prescriptions stay in history, not the active calendar.
        if (record["status"]?.toString() == "복용 완료") return false
        val start = record["scheduleStartDate"]?.toString() ?: return false
        val days = record["prescriptionDays"]?.toString()?.toIntOrNull() ?: return false
        if (days !in 1..365) return false
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply { isLenient = false }
            val startDay = Calendar.getInstance().apply { time = fmt.parse(start)!! }
            val targetDay = Calendar.getInstance().apply { time = fmt.parse(date)!! }
            val endDay = startDay.clone() as Calendar
            endDay.add(Calendar.DAY_OF_MONTH, days - 1)
            !targetDay.before(startDay) && !targetDay.after(endDay)
        } catch (_: Exception) {
            false
        }
    }

    private fun openEditor(
        existingId: String? = null,
        existing: Map<String, Any>? = null,
        prefillFromBenefit: Boolean = false
    ) {
        val isEditing = existingId != null
        if (!isEditing && !prefillFromBenefit) {
            hospitalName = ""; hospitalAddress = ""; hospitalPhone = ""; hospitalCategory = ""
            hospitalLat = 0.0; hospitalLng = 0.0
        } else {
            hospitalName = existing?.get("hospitalName")?.toString().orEmpty()
            hospitalAddress = existing?.get("hospitalAddress")?.toString().orEmpty()
            hospitalPhone = existing?.get("hospitalPhone")?.toString().orEmpty()
            hospitalCategory = existing?.get("hospitalCategory")?.toString().orEmpty()
            hospitalLat = (existing?.get("hospitalLatitude") as? Number)?.toDouble() ?: 0.0
            hospitalLng = (existing?.get("hospitalLongitude") as? Number)?.toDouble() ?: 0.0
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(
            dp(20), dp(12), dp(20), dp(12)
        )
        }
        val dialogScroll = ScrollView(this).apply { addView(container) }
        // 병원 검색은 예약 확정이 아니므로 초기 상태는 진료 기록이며 사용자가 선택합니다.
        var booking = existing?.get("recordType") == "booking"
        val modeButton = button(
            container, if (booking) "예약 예정  ✓   |   진료 완료" else "진료 완료  ✓   |   예약 예정", false
        ) { }

        fun updateMode() {
            modeButton.text = if (booking) "예약 예정 ✓  (눌러 변경)" else "진료 완료 ✓  (눌러 변경)"
        }
        updateMode()
        modeButton.setOnClickListener { booking = !booking; updateMode() }
        val dateLabel =
            button(container, "날짜: ${existing?.get("visitDate") ?: selectedDate}", false) { }
        var formDate = existing?.get("visitDate")?.toString() ?: selectedDate
        dateLabel.setOnClickListener {
            val p = formDate.split("-").map { it.toInt() }
            DatePickerDialog(this, { _, y, m, d ->
                formDate = String.format(Locale.KOREA, "%04d-%02d-%02d", y, m + 1, d)
                dateLabel.text = "날짜: $formDate"
            }, p[0], p[1] - 1, p[2]).show()
        }
        var formTime = existing?.get("visitTime")?.toString().orEmpty()
        val timeButton = button(container, "시간: ${formTime.ifBlank { "선택 안 함" }}", false) { }
        timeButton.setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(this, { _, h, m ->
                formTime = String.format(Locale.KOREA, "%02d:%02d", h, m)
                timeButton.text = "시간: $formTime"
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }
        title(container, "예약 알림", 16)
        val reminderDay = CheckBox(this).apply {
            text = "하루 전 알림"
            isChecked = (existing?.get("remindDay") as? Boolean) ?: true
        }
        val reminderHour = CheckBox(this).apply {
            text = "1시간 전 알림"
            isChecked = (existing?.get("remindHour") as? Boolean) ?: true
        }
        container.addView(reminderDay)
        container.addView(reminderHour)
        fun updateReminderVisibility() {
            reminderDay.visibility = if (booking) View.VISIBLE else View.GONE
            reminderHour.visibility = if (booking) View.VISIBLE else View.GONE
        }
        updateReminderVisibility()
        modeButton.setOnClickListener {
            booking = !booking
            updateMode()
            updateReminderVisibility()
        }
        title(container, "병원 선택", 16)
        hospitalLabel = label(
            container,
            if (hospitalName.isBlank()) "병원을 선택해 주세요" else "$hospitalName\n$hospitalAddress",
            15,
            ink
        )
        button(container, "실제 병원 찾아 선택하기", false) {
            picker.launch(
                Intent(this, HospitalActivity::class.java).putExtra(
                    "selectHospitalMode", true
                )
            )
        }
        title(container, "진료과 (자동 입력 · 수정 가능)", 16)
        departmentField = EditText(this).apply {
            hint = "예: 이비인후과"
            setSingleLine(true)
            setText(
                existing?.get("department")?.toString() ?: guessDepartment(
                    hospitalCategory, hospitalName
                )
            )
        }
        container.addView(departmentField)
        title(container, "증상 / 방문 목적 (여러 개 선택)", 16)
        val chosen = linkedSetOf<String>()
        existing?.get("symptoms")?.toString()?.split(",")?.map { it.trim() }
            ?.filter { it.isNotBlank() }?.let { chosen.addAll(it) }
        val symptomGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(symptomGrid)
        val choices = listOf(
            "감기", "편도염", "두통", "복통", "발열", "기침", "인후통", "알레르기", "피부질환", "치통", "근육통", "정기검진", "기타"
        )
        choices.chunked(3).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            symptomGrid.addView(row)
            rowItems.forEach { symptom ->
                val cb = CheckBox(this).apply {
                    text = symptom
                    textSize = 13f
                    isChecked = symptom in chosen
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) chosen.add(symptom) else chosen.remove(
                            symptom
                        )
                    }
                }
                row.addView(cb, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
        }
        title(container, "추가 메모 (선택)", 16)
        val memo = EditText(this).apply {
            hint = "특별한 증상, 검사 결과, 의사 안내사항 등"
            minLines = 3
            gravity = Gravity.TOP
            setText(
                existing?.get("treatment")?.toString() ?: if (prefillFromBenefit) "AI 추천 혜택: ${
                    intent.getStringExtra("benefitTitle").orEmpty()
                } (실제 예약 여부는 별도 확인)" else ""
            )
        }
        container.addView(memo)
        val dialog = AlertDialog.Builder(this).setTitle(if (isEditing) "건강 일정 수정" else "건강 일정 등록")
            .setView(dialogScroll).setNegativeButton("취소", null).setPositiveButton("저장", null)
            .create()
        dialog.setOnDismissListener { hospitalLabel = null; departmentField = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (hospitalName.isBlank()) {
                    toast("병원을 선택해 주세요."); return@setOnClickListener
                }
                if (booking && formTime.isBlank()) {
                    toast("예약 시간을 선택해 주세요."); return@setOnClickListener
                }
                if (chosen.isEmpty() && memo.text.isBlank()) {
                    toast("증상 또는 메모를 입력해 주세요."); return@setOnClickListener
                }
                val uid =
                    auth.currentUser?.uid ?: run { toast("로그인이 필요해요."); return@setOnClickListener }
                val data = hashMapOf<String, Any>(
                    "visitDate" to formDate,
                    "visitTime" to formTime,
                    "recordType" to if (booking) "booking" else "visit",
                    "remindDay" to (booking && reminderDay.isChecked),
                    "remindHour" to (booking && reminderHour.isChecked),
                    "hospitalName" to hospitalName,
                    "hospitalAddress" to hospitalAddress,
                    "hospitalPhone" to hospitalPhone,
                    "hospitalCategory" to hospitalCategory,
                    "hospitalLatitude" to hospitalLat,
                    "hospitalLongitude" to hospitalLng,
                    "department" to departmentField!!.text.toString().trim(),
                    "symptoms" to chosen.joinToString(", "),
                    "treatment" to memo.text.toString().trim()
                )
                if (!isEditing) data["createdAt"] = com.google.firebase.Timestamp.now()
                val collection = db.collection("users").document(uid).collection("visitRecords")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                val task =
                    if (existingId == null) collection.add(data) else collection.document(existingId)
                        .set(data, com.google.firebase.firestore.SetOptions.merge())
                task.addOnSuccessListener {
                    dialog.dismiss(); selectedDate = formDate; syncCalendar(); loadRecords(); toast(
                    "저장했어요."
                )
                }.addOnFailureListener { e ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                        true; toast("저장 실패: ${e.localizedMessage}")
                }
            }
        }
        dialog.show()
    }

    private fun syncCalendar() {
        val p = selectedDate.split("-").map { it.toInt() }
        visibleMonth.set(Calendar.YEAR, p[0])
        visibleMonth.set(Calendar.MONTH, p[1] - 1)
        renderCalendar()
    }

    private fun renderCalendar() {
        monthLabel.text = String.format(
            Locale.KOREA,
            "%d년 %d월",
            visibleMonth.get(Calendar.YEAR),
            visibleMonth.get(Calendar.MONTH) + 1
        )
        calendarGrid.removeAllViews()
        val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")
        val weekdayRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        calendarGrid.addView(weekdayRow)
        weekdays.forEachIndexed { index, day ->
            weekdayRow.addView(TextView(this).apply {
                text = day; gravity = Gravity.CENTER; textSize = 12f
                setTextColor(if (index == 0) Color.rgb(210, 96, 96) else muted)
            }, LinearLayout.LayoutParams(0, dp(32), 1f))
        }
        val first = (visibleMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val maxDay = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cellCount = ((offset + maxDay + 6) / 7) * 7
        for (weekStart in 0 until cellCount step 7) {
            val week = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            calendarGrid.addView(week)
            for (column in 0..6) {
                val day = weekStart + column - offset + 1
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    minimumHeight = dp(52)
                }
                week.addView(cell, LinearLayout.LayoutParams(0, dp(52), 1f))
                if (day !in 1..maxDay) continue
                val date = String.format(
                    Locale.KOREA,
                    "%04d-%02d-%02d",
                    visibleMonth.get(Calendar.YEAR),
                    visibleMonth.get(Calendar.MONTH) + 1,
                    day
                )
                if (date == selectedDate) cell.background = GradientDrawable().apply {
                    setColor(Color.rgb(233, 238, 255)); cornerRadius = dp(10).toFloat()
                }
                cell.addView(TextView(this).apply {
                    text = day.toString(); gravity = Gravity.CENTER; textSize = 15f
                    setTextColor(if (column == 0) Color.rgb(210, 96, 96) else ink)
                    if (date == selectedDate) setTypeface(null, Typeface.BOLD)
                })
                val dayRecords = records.filter { it.second["visitDate"] == date }
                val dots = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                }
                cell.addView(dots, LinearLayout.LayoutParams(-1, dp(12)))
                val hasVisit = dayRecords.any { it.second["recordType"] != "booking" }
                val hasBooking = dayRecords.any { it.second["recordType"] == "booking" }
                val hasMedicine = medicineRecords.any { medicineOnDate(it.second, date) }
                listOf(
                    hasVisit to purple,
                    hasBooking to mint,
                    hasMedicine to medicineBlue
                ).forEach { (show, color) ->
                    if (show) {
                        val dot = View(this).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL; setColor(color)
                            }
                        }
                        dots.addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                            leftMargin = dp(2); rightMargin = dp(2)
                        })
                    }
                }
                cell.setOnClickListener { selectedDate = date; renderCalendar(); renderDay() }
            }
        }
    }

    private fun showDetails(id: String, map: Map<String, Any>) {
        val name = map["hospitalName"]?.toString().orEmpty()
        val address = map["hospitalAddress"]?.toString().orEmpty()
        val phone = map["hospitalPhone"]?.toString().orEmpty()
        val lat = (map["hospitalLatitude"] as? Number)?.toDouble() ?: 0.0
        val lng = (map["hospitalLongitude"] as? Number)?.toDouble() ?: 0.0
        val kind = if (map["recordType"] == "booking") "예약 예정" else "진료 완료"
        val message =
            "$kind\n날짜: ${map["visitDate"]}\n시간: ${map["visitTime"] ?: ""}\n병원: $name\n주소: $address\n전화: $phone\n진료과: ${map["department"] ?: ""}\n증상: ${map["symptoms"] ?: ""}\n메모: ${map["treatment"] ?: ""}"
        AlertDialog.Builder(this).setTitle("건강 일정 상세").setMessage(message)
            .setPositiveButton("지도 보기") { _, _ ->
                val uri =
                    if (lat != 0.0 && lng != 0.0) Uri.parse("geo:0,0?q=$lat,$lng(${Uri.encode(name)})")
                    else Uri.parse("geo:0,0?q=${Uri.encode(name + " " + address)}")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                    toast("지도 앱이 없어요.")
                }
            }.setNeutralButton("수정 / 삭제") { _, _ ->
                AlertDialog.Builder(this).setItems(arrayOf("수정하기", "삭제하기", "전화 걸기")) { _, which ->
                    when (which) {
                        0 -> openEditor(id, map)
                        1 -> AlertDialog.Builder(this).setMessage("이 기록을 삭제할까요?")
                            .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                                db.collection("users").document(uid).collection("visitRecords")
                                    .document(id).delete()
                                    .addOnSuccessListener { loadRecords(); toast("삭제했어요.") }
                                    .addOnFailureListener { toast("삭제 실패") }
                            }.show()

                        2 -> if (phone.isNotBlank()) startActivity(
                            Intent(
                                Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace("-", "")}")
                            )
                        ) else toast("전화번호 정보가 없어요.")
                    }
                }.show()
            }.setNegativeButton("닫기", null).show()
    }

    private fun guessDepartment(category: String, name: String): String {
        val s = "$category $name"
        return listOf(
            "이비인후과",
            "내과",
            "소아청소년과",
            "정형외과",
            "피부과",
            "치과",
            "안과",
            "산부인과",
            "비뇨의학과",
            "신경과",
            "외과",
            "정신건강의학과",
            "재활의학과",
            "가정의학과",
            "한의원"
        ).firstOrNull { s.contains(it) } ?: ""
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun card(parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background =
                GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(15).toFloat() }
            elevation = dp(2).toFloat()
        }
        parent.addView(
            c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(10), 0, dp(10)) })
        return c
    }

    private fun label(parent: LinearLayout, value: String, size: Int, color: Int): TextView {
        val v = TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(color); setPadding(
            0, dp(5), 0, dp(5)
        )
        }
        parent.addView(v)
        return v
    }

    private fun title(parent: LinearLayout, value: String, size: Int): TextView =
        label(parent, value, size, ink).apply { setTypeface(null, Typeface.BOLD) }

    private fun button(
        parent: LinearLayout, value: String, primary: Boolean, action: () -> Unit
    ): Button {
        val b = Button(this).apply {
            text = value
            textSize = 15f
            isAllCaps = false
            setTextColor(if (primary) Color.WHITE else ink)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (primary) purple else Color.rgb(
                    233, 238, 255
                )
            )
            setOnClickListener { action() }
        }
        parent.addView(b, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        return b
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 501
            )
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

