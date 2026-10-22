package com.example.myapplication

import android.content.Intent
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Personalised benefit guide, v1.
 * The recommendations are estimates, NOT official NHIS eligibility decisions.
 * Stores only user-entered fields in users/{uid}/healthProfile/benefits.
 */
class PersonalHealthActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val ink = 0xFF20304B.toInt()
    private val muted = 0xFF708099.toInt()
    private val blue = 0xFF5D88D9.toInt()
    private lateinit var yearInput: EditText
    private lateinit var sexInput: Spinner
    private lateinit var insuranceInput: Spinner
    private lateinit var lastScalingInput: EditText
    private lateinit var resultContainer: LinearLayout
    private lateinit var aiButton: TextView
    private lateinit var aiResultContainer: LinearLayout
    private lateinit var saveRecommendationConsent: CheckBox
    private lateinit var profilePreview: TextView
    private lateinit var profileConsent: CheckBox
    private var profileText = ""
    private var profileLoaded = false
    private var profileLoading = false
    private var profileRequestVersion = 0
    private lateinit var aiLoading: ProgressBar
    private val aiClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()
    private var selectedBenefitTitle = ""
    private val hospitalPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { hospital ->
                    val calendar = Intent(this, HealthCalendarActivity::class.java).apply {
                        putExtra("fromBenefitRecommendation", true)
                        putExtra("benefitTitle", selectedBenefitTitle)
                        listOf(
                            "hospitalName",
                            "hospitalAddress",
                            "hospitalPhone",
                            "hospitalCategory"
                        )
                            .forEach { putExtra(it, hospital.getStringExtra(it).orEmpty()) }
                        putExtra(
                            "hospitalLatitude",
                            hospital.getDoubleExtra("hospitalLatitude", 0.0)
                        )
                        putExtra(
                            "hospitalLongitude",
                            hospital.getDoubleExtra("hospitalLongitude", 0.0)
                        )
                    }
                    startActivity(calendar)
                }
            }
        }

    private fun findHospitalForBenefit(title: String) {
        selectedBenefitTitle = title
        val keyword = when {
            title.contains("스케일링") || title.contains("치과") || title.contains("구강") -> "치과"
            title.contains("자궁경부") || title.contains("유방") -> "산부인과"
            title.contains("예방접종") || title.contains("백신") || title.contains("HPV") -> "예방접종 병원"
            title.contains("검진") || title.contains("암") -> "건강검진 병원"
            else -> "병원"
        }
        hospitalPicker.launch(Intent(this, HospitalActivity::class.java).apply {
            putExtra("selectHospitalMode", true)
            putExtra("hospitalSearchKeyword", keyword)
        })
    }

    private val currentYear get() = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFF8FAFD.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(36))
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(label("‹  뒤로", 16f, muted, true).apply {
            setPadding(0, 0, 0, dp(20)); setOnClickListener { finish() }
        })
        root.addView(label("나에게 맞는 건강 혜택", 25f, ink, true))
        root.addView(label("기본 정보를 입력하면 예상 대상과 관리 시기를 안내해요.", 13f, muted, false).apply {
            setPadding(0, dp(8), 0, dp(18))
        })
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 20)
            setPadding(dp(17), dp(17), dp(17), dp(17))
        }
        form.addView(label("출생연도", 14f, ink, true))
        yearInput = EditText(this).apply {
            hint = "예: 2004"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            textSize = 16f
        }
        form.addView(yearInput)
        form.addView(label("국가검진 안내에 사용할 성별", 14f, ink, true).apply { setPadding(0, dp(14), 0, 0) })
        sexInput = spinner(listOf("선택 안 함", "여성", "남성"))
        form.addView(sexInput)
        form.addView(label("건강보험 자격 유형", 14f, ink, true).apply { setPadding(0, dp(14), 0, 0) })
        insuranceInput = spinner(
            listOf(
                "모름 / 선택 안 함",
                "지역가입자",
                "직장가입자(사무직)",
                "직장가입자(비사무직)",
                "피부양자",
                "의료급여 수급권자"
            )
        )
        form.addView(insuranceInput)
        form.addView(label("올해 스케일링 이용 여부", 14f, ink, true).apply { setPadding(0, dp(14), 0, 0) })
        lastScalingInput = EditText(this).apply {
            hint = "예: 이용함 / 이용 안 함 / 모름"
            textSize = 14f
            setSingleLine(true)
        }
        form.addView(lastScalingInput)
        root.addView(form)
        root.addView(
            action("내 건강 혜택 확인하기") { calculateAndSave() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(15) })
        resultContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            resultContainer,
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        val profileBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 20)
            setPadding(dp(17), dp(16), dp(17), dp(16))
        }
        profileBox.addView(label("💗 마이페이지 건강정보 연동", 17f, ink, true))
        profileBox.addView(label("AI 분석에 활용할 정보를 먼저 확인해 주세요.", 13f, muted, false).apply {
            setPadding(0, dp(6), 0, dp(10))
        })
        profilePreview = label("건강정보를 불러오려면 아래 버튼을 눌러 주세요.", 13f, ink, false)
        profileBox.addView(profilePreview)
        profileBox.addView(
            action("↻ 내 건강정보 불러오기") { loadHealthProfile() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        profileConsent = CheckBox(this).apply {
            text = "위 건강정보를 외부 AI 분석에 사용하는 데 동의합니다."
            textSize = 13f
            isChecked = false
        }
        profileBox.addView(profileConsent)
        root.addView(profileBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        saveRecommendationConsent = CheckBox(this).apply {
            text = "AI 추천 결과를 내 계정에 저장합니다 (선택)"
            textSize = 13f
            isChecked = false
        }
        root.addView(saveRecommendationConsent)
        root.addView(
            action("📚 지난 AI 추천 기록 보기") { showRecommendationHistory() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        aiButton = action("✨ AI 맞춤 분석하기") { confirmAIRequest() }
        root.addView(aiButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        aiLoading = ProgressBar(this).apply { visibility = android.view.View.GONE }
        root.addView(
            aiLoading,
            LinearLayout.LayoutParams(-2, -2)
                .apply { topMargin = dp(12); gravity = Gravity.CENTER_HORIZONTAL })
        aiResultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
        }
        root.addView(
            aiResultContainer,
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(
            label(
                "※ 예상 안내이며 실제 국가검진 대상·건강보험 적용·지원 여부는 국민건강보험공단 및 관련 기관에서 확인해야 해요. 입력 정보는 앱 계정에 저장됩니다.",
                12f,
                muted,
                false
            ).apply {
                setPadding(0, dp(16), 0, 0)
            })
        if (intent.getBooleanExtra("openAIHistory", false)) showRecommendationHistory()
        // 재진입 시 이전 입력과 분석 결과를 자동 복원하지 않습니다.
        // Firestore 저장은 유지하지만 화면은 새 입력 상태로 시작합니다.
    }

    private fun calculateAndSave() {
        val birth = yearInput.text.toString().toIntOrNull()
        if (birth == null || birth !in 1900..currentYear) {
            yearInput.error = "올바른 출생연도를 입력해 주세요"
            return
        }
        val sex = sexInput.selectedItemPosition
        val insurance = insuranceInput.selectedItemPosition
        val scaling = lastScalingInput.text.toString().trim()
        showResults(birth, sex, insurance, scaling)
        aiResultContainer.removeAllViews()
        aiResultContainer.visibility = android.view.View.GONE
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "로그인하면 입력 정보가 저장돼요.", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("users").document(uid).collection("healthProfile").document("benefits")
            .set(
                mapOf(
                    "birthYear" to birth, "screeningSex" to sex, "insuranceType" to insurance,
                    "scalingThisYear" to scaling, "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnFailureListener {
                Toast.makeText(this, "정보 저장에 실패했어요.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showResults(birth: Int, sex: Int, insurance: Int, scaling: String) {
        resultContainer.removeAllViews()
        resultContainer.addView(label("$currentYear 나의 예상 건강 혜택", 19f, ink, true))
        val ageThisYear = currentYear - birth
        val parityMatches = (birth % 2) == (currentYear % 2)
        val general = when {
            ageThisYear < 20 -> "20세 미만은 자격 유형에 따라 일반검진 대상이 달라져요. 공식 조회가 필요해요."
            insurance == 3 -> "비사무직 직장가입자는 일반적으로 매년 검진 대상이에요. 실제 대상 여부를 확인해 주세요."
            insurance == 0 -> "건강보험 자격 유형이 미확인 상태예요. 공식 대상자 조회가 필요해요."
            parityMatches -> "출생연도 기준으로 올해 일반검진 대상일 가능성이 있어요. 자격·검진 이력에 따라 달라질 수 있어요."
            else -> "출생연도 기준으로 올해 정기 검진 대상이 아닐 수 있어요. 예외·이월 여부는 공식 조회해 주세요."
        }
        resultCard("일반건강검진", general, "예상 안내")
        if (sex == 1 && ageThisYear >= 20) {
            resultCard(
                "자궁경부암 국가검진", if (parityMatches)
                    "20세 이상 여성의 2년 주기 검진 기준상 올해 대상일 가능성이 있어요."
                else "20세 이상 여성의 2년 주기 검진이에요. 올해 대상 여부는 공식 조회해 주세요.", "예상 안내"
            )
        } else if (sex == 0) {
            resultCard("국가암검진", "검진 안내에 사용할 성별을 선택하면 해당할 수 있는 검진을 더 구체적으로 보여드려요.", "정보 필요")
        }
        if (ageThisYear >= 40) {
            resultCard(
                "연령별 국가암검진",
                "40세 이상부터 검진 종류에 따라 위암·유방암 등 대상 여부를 확인할 필요가 있어요. 검사별 성별·연령·주기가 달라요.",
                "상세 확인"
            )
        }
        resultCard(
            "치과 스케일링", when {
                ageThisYear < 19 -> "만 19세 이상부터 적용되는 일반적인 연 1회 치석제거 보험 혜택은 아직 해당하지 않을 수 있어요."
                scaling.contains("안") -> "올해 이용하지 않았다고 입력했어요. 실제 보험 적용 가능 여부는 치과에서 확인해 주세요."
                scaling.contains("이용함") || scaling.contains("받음") -> "올해 이용했다고 기록했어요. 추가 시술의 보험 적용 여부는 치과에서 확인해 주세요."
                else -> "만 19세 이상은 일반적인 전악 치석제거에 연 1회 건강보험 적용이 가능해요. 올해 이용 여부를 확인해 주세요."
            }, "보험 기준 안내"
        )
        resultCard(
            "예방접종",
            "접종 종류·과거 접종 이력·건강 상태에 따라 권장 시기와 지원 대상이 달라요. 접종 기록을 확인한 뒤 맞춤 안내를 확장할 예정이에요.",
            "접종 이력 필요"
        )
        resultContainer.addView(action("건강 캘린더에서 일정 관리") {
            startActivity(Intent(this, HealthCalendarActivity::class.java))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun loadHealthProfile() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            profilePreview.text = "로그인 후 건강정보를 불러올 수 있어요."
            profileConsent.isChecked = false
            profileLoaded = false
            return
        }
        val version = ++profileRequestVersion
        profileLoading = true
        profileLoaded = false
        profileText = ""
        profileConsent.isChecked = false
        profilePreview.text = "건강정보를 불러오는 중이에요..."
        val userTask = db.collection("users").document(uid).get()
        val medicineTask = db.collection("users").document(uid).collection("medicineRecords").get()
        com.google.android.gms.tasks.Tasks.whenAllComplete(userTask, medicineTask)
            .addOnCompleteListener {
                if (version != profileRequestVersion || isFinishing || isDestroyed) return@addOnCompleteListener
                profileLoading = false
                if (!userTask.isSuccessful || !medicineTask.isSuccessful) {
                    profilePreview.text = "건강정보를 모두 불러오지 못했어요. 다시 시도해 주세요."
                    return@addOnCompleteListener
                }
                val user = userTask.result
                val allergy = user?.getString("allergy").orEmpty().trim()
                val disease = user?.getString("disease").orEmpty().trim()
                val medicine = user?.getString("medicine").orEmpty().trim()
                val activeMedicines = medicineTask.result?.documents.orEmpty().flatMap { record ->
                    val items = record.get("medicines") as? List<*> ?: emptyList<Any>()
                    items.mapNotNull { raw ->
                        val item = raw as? Map<*, *> ?: return@mapNotNull null
                        val name = item["medicineName"] as? String
                        val status = item["status"] as? String ?: "확인 필요"
                        name?.trim()?.takeIf { it.isNotEmpty() && status == "복용 중" }
                    }
                }.distinct()
                profileText = listOf(
                    "알레르기: ${allergy.ifBlank { "등록 없음" }}",
                    "지병: ${disease.ifBlank { "등록 없음" }}",
                    "직접 입력한 복용약: ${medicine.ifBlank { "등록 없음" }}",
                    "처방전에서 복용 중인 약: ${activeMedicines.joinToString(", ").ifBlank { "등록 없음" }}"
                ).joinToString("\n")
                profilePreview.text = profileText
                profileLoaded = true
            }
    }

    private fun confirmAIRequest() {
        val birth = yearInput.text.toString().toIntOrNull()
        if (birth == null || birth !in 1900..currentYear) {
            yearInput.error = "올바른 출생연도를 입력해 주세요"
            return
        }
        if (profileLoading) {
            Toast.makeText(this, "건강정보를 불러오는 중이에요. 잠시 기다려 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val includeProfile = profileLoaded && profileConsent.isChecked
        val notice = if (includeProfile) {
            "출생연도, 검진 안내용 성별, 보험 유형, 스케일링 여부와 화면에 표시된 알레르기·지병·복용약 정보가 외부 AI 서비스에 전송됩니다. 보호자 정보와 연락처는 전송하지 않아요."
        } else {
            "출생연도, 검진 안내용 성별, 보험 유형, 스케일링 여부만 외부 AI 서비스에 전송됩니다. 마이페이지 건강정보는 전송하지 않아요."
        }
        AlertDialog.Builder(this)
            .setTitle("AI 분석을 요청할까요?")
            .setMessage("$notice\n\nAI 답변은 공식 자격 확인이나 의료 진단이 아닙니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("동의하고 분석") { _, _ -> requestAI(birth, includeProfile) }
            .show()
    }

    private fun requestAI(birth: Int, includeProfile: Boolean) {
        val apiKey = getString(R.string.factchat_api_key)
        if (apiKey.isBlank()) {
            Toast.makeText(this, "AI API 키가 설정되지 않았어요.", Toast.LENGTH_SHORT).show()
            return
        }
        val sex = sexInput.selectedItem.toString()
        val insurance = insuranceInput.selectedItem.toString()
        val scaling = lastScalingInput.text.toString().trim().take(40).ifBlank { "모름" }
        val system = """너는 한국 공공 건강 혜택 안내 도우미다. 현재 연도는 $currentYear 년이다.
            입력 정보만 사용하고 진단, 수검 이력, 실제 대상 자격, 가격을 추측하지 마라.
            검진 자격·지원 여부는 공식 확인이 필요하며, 근거가 불확실한 혜택은 추천하지 마라.
            알레르기·지병·복용약 정보가 있더라도 그것만으로 질환을 진단하거나 특정 검진·접종이 필요하다고 단정하지 마라.
            건강정보는 확인할 조건 및 의료진 상담 시 유의사항을 개인화하는 데만 참고하라.
            답변은 반드시 JSON 객체 하나만 반환한다. 마크다운, 코드펜스, 표, 설명 문장 금지.
            형식: {"summary":"짧은 한 줄 요약", "benefits":[{"title":"혜택명","reason":"추천 이유 1~2문장","condition":"확인할 조건 1문장","action":"다음 행동 1문장"}],"note":"주의사항 1문장"}
            benefits는 최대 3개, 각 문자열은 짧고 읽기 쉽게 작성하라. 정보가 부족하면 부족하다고 명시하라.
        """.trimIndent()
        val user =
            "출생연도: $birth, 검진 안내용 성별: $sex, 건강보험 유형: $insurance, 올해 스케일링: $scaling. 공식 자격을 확정하지 않고 확인할 만한 혜택을 추천해줘."
        val profileContext =
            if (includeProfile && profileLoaded) " 추가 건강정보: ${profileText.take(1800)}." else ""
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user + profileContext))
        val payload = JSONObject().put("model", "claude-sonnet-4-6").put("messages", messages)
            .put("stream", false)
        val saveResult = saveRecommendationConsent.isChecked
        val request = Request.Builder()
            .url("https://factchat-cloud.mindlogic.ai/v1/gateway/chat/completions/")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        aiButton.isEnabled = false
        aiButton.alpha = 0.6f
        aiLoading.visibility = android.view.View.VISIBLE
        aiResultContainer.visibility = android.view.View.GONE
        aiClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showAIResponse("AI 연결에 실패했어요. 인터넷 연결을 확인해 주세요.") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val content = response.body?.string().orEmpty()
                    val answer = if (!response.isSuccessful) {
                        "AI 요청에 실패했어요. 오류 코드: ${response.code}"
                    } else {
                        try {
                            JSONObject(content).getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content")
                        } catch (e: Exception) {
                            "AI 답변을 읽지 못했어요."
                        }
                    }
                    runOnUiThread {
                        showAIResponse(answer)
                        if (response.isSuccessful && saveResult) saveRecommendation(answer)
                    }
                }
            }
        })
    }

    private fun showAIResponse(answer: String) {
        if (isFinishing || isDestroyed) return
        aiLoading.visibility = android.view.View.GONE
        aiButton.isEnabled = true
        aiButton.alpha = 1f
        aiResultContainer.removeAllViews()
        aiResultContainer.visibility = android.view.View.VISIBLE

        aiResultContainer.addView(label("✨ AI 맞춤 추천 결과", 21f, ink, true))
        aiResultContainer.addView(label("입력하신 정보를 기준으로 확인해 볼 혜택이에요.", 13f, muted, false).apply {
            setPadding(0, dp(5), 0, dp(8))
        })

        try {
            // JSON 외에 코드펜스를 붙이는 모델 응답에도 대응합니다.
            val clean = answer.trim().removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val data = JSONObject(clean)
            val summary = data.optString("summary")
            if (summary.isNotBlank()) {
                aiResultContainer.addView(infoPanel(summary, 0xFFE9F2FF.toInt()))
            }
            val benefits = data.optJSONArray("benefits") ?: JSONArray()
            for (i in 0 until minOf(benefits.length(), 3)) {
                val item = benefits.optJSONObject(i) ?: continue
                aiResultContainer.addView(
                    benefitPanel(
                        item.optString("title", "건강 혜택"),
                        item.optString("reason"),
                        item.optString("condition"),
                        item.optString("action"), i
                    )
                )
            }
            if (benefits.length() == 0) {
                aiResultContainer.addView(
                    infoPanel(
                        "현재 입력만으로 구체적인 혜택을 추천하기 어려워요. 공식 기관에서 확인해 주세요.",
                        Color.WHITE
                    )
                )
            }
            val note = data.optString("note")
            if (note.isNotBlank()) {
                aiResultContainer.addView(label("※ $note", 12f, muted, false).apply {
                    setPadding(0, dp(12), 0, 0)
                })
            }
        } catch (_: Exception) {
            // 서버가 JSON 형식을 지키지 않더라도 오류 없이 읽을 수 있도록 표시합니다.
            val plain = answer.replace(Regex("(?m)^#{1,6}\\s*"), "")
                .replace("**", "").replace("```", "")
            aiResultContainer.addView(infoPanel(plain, Color.WHITE))
        }
        aiResultContainer.addView(
            label(
                "※ AI의 예상 안내입니다. 실제 검진 대상과 건강보험 적용 여부는 공식 기관에서 확인해 주세요.",
                12f, muted, false
            ).apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun saveRecommendation(answer: String) {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "로그인 후 기록을 저장할 수 있어요.", Toast.LENGTH_SHORT).show()
            return
        }
        val clean =
            answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = try {
            JSONObject(clean)
        } catch (_: Exception) {
            return
        }
        val benefits = json.optJSONArray("benefits") ?: return
        if (benefits.length() == 0) return
        val items = (0 until minOf(benefits.length(), 3)).mapNotNull { i ->
            benefits.optJSONObject(i)?.let { item ->
                mapOf(
                    "title" to item.optString("title").take(150),
                    "reason" to item.optString("reason").take(1000),
                    "condition" to item.optString("condition").take(1000),
                    "action" to item.optString("action").take(1000)
                )
            }
        }
        if (items.isEmpty()) return
        db.collection("users").document(uid).collection("aiBenefitRecommendations")
            .add(
                mapOf(
                    "createdAt" to Timestamp.now(),
                    "summary" to json.optString("summary").take(1000),
                    "note" to json.optString("note").take(1000),
                    "benefits" to items
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "AI 추천 결과를 저장했어요.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "추천 기록 저장에 실패했어요.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRecommendationHistory() {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "로그인이 필요해요.", Toast.LENGTH_SHORT).show(); return
        }
        db.collection("users").document(uid).collection("aiBenefitRecommendations")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(30).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    AlertDialog.Builder(this).setTitle("AI 추천 기록")
                        .setMessage("아직 저장한 추천 결과가 없어요.").setPositiveButton("확인", null).show()
                    return@addOnSuccessListener
                }
                val docs = snapshot.documents
                val fmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
                val titles = docs.map { doc ->
                    val date = doc.getTimestamp("createdAt")?.toDate()?.let(fmt::format) ?: "날짜 없음"
                    val names = (doc.get("benefits") as? List<*>)?.mapNotNull {
                        (it as? Map<*, *>)?.get("title")?.toString()
                    }?.joinToString(", ").orEmpty()
                    "$date\n$names"
                }.toTypedArray()
                AlertDialog.Builder(this).setTitle("지난 AI 추천 기록")
                    .setItems(titles) { _, index ->
                        val doc = docs[index]
                        val details = buildString {
                            append(doc.getString("summary").orEmpty()).append("\n\n")
                            (doc.get("benefits") as? List<*>)?.forEach { raw ->
                                val item = raw as? Map<*, *> ?: return@forEach
                                append("● ").append(item["title"]?.toString().orEmpty()).append("\n")
                                append("추천 이유: ").append(item["reason"]?.toString().orEmpty()).append("\n")
                                append("확인할 조건: ").append(item["condition"]?.toString().orEmpty()).append("\n")
                                append("다음 행동: ").append(item["action"]?.toString().orEmpty()).append("\n")
                            }
                            append(doc.getString("note").orEmpty())
                        }
                        AlertDialog.Builder(this).setTitle("저장된 AI 추천")
                            .setMessage(details + "\n\n※ 실제 지원 대상 여부는 공식 기관에서 확인하세요.")
                            .setNeutralButton("삭제") { _, _ ->
                                AlertDialog.Builder(this).setMessage("이 추천 기록을 삭제할까요?")
                                    .setNegativeButton("취소", null)
                                    .setPositiveButton("삭제") { _, _ ->
                                        doc.reference.delete().addOnSuccessListener {
                                            Toast.makeText(this, "삭제했어요.", Toast.LENGTH_SHORT)
                                                .show()
                                        }.addOnFailureListener {
                                            Toast.makeText(this, "삭제에 실패했어요.", Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    }.show()
                            }
                            .setPositiveButton("닫기", null).show()
                    }.setNegativeButton("닫기", null).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "추천 기록을 불러오지 못했어요.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun infoPanel(message: String, backgroundColor: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(backgroundColor, 18)
            setPadding(dp(17), dp(16), dp(17), dp(16))
            addView(label(message, 14f, ink, false))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }

    private fun benefitPanel(
        title: String,
        reason: String,
        condition: String,
        next: String,
        index: Int
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 20)
            setPadding(dp(18), dp(17), dp(18), dp(18))
        }
        val pastel = listOf(0xFFE9F1FF.toInt(), 0xFFFFEFF3.toInt(), 0xFFECF8F2.toInt())[index % 3]
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val icon = label(listOf("🦷", "🩺", "💗")[index % 3], 23f, ink, false).apply {
            gravity = Gravity.CENTER
            background = rounded(pastel, 12)
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(
            label(title, 17f, ink, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        card.addView(header)
        fun section(heading: String, content: String) {
            if (content.isBlank()) return
            card.addView(label(heading, 12f, blue, true).apply { setPadding(0, dp(14), 0, dp(4)) })
            card.addView(label(content, 14f, ink, false))
        }
        section("추천 이유", reason)
        section("확인할 조건", condition)
        section("다음 행동", next)
        card.addView(
            action("🏥 관련 병원 찾기 · 일정 등록") { findHospitalForBenefit(title) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        return card
    }

    private fun resultCard(title: String, description: String, status: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 18)
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label(title, 16f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(label(status, 11f, blue, true))
        card.addView(heading)
        card.addView(label(description, 13f, muted, false).apply { setPadding(0, dp(9), 0, 0) })
        card.addView(
            action("🏥 관련 병원 찾기 · 일정 등록") { findHospitalForBenefit(title) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        resultContainer.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(11) })
    }

    private fun spinner(options: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@PersonalHealthActivity,
            android.R.layout.simple_spinner_dropdown_item,
            options
        )
    }

    private fun action(title: String, click: () -> Unit) =
        label(title, 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(17), dp(14), dp(17))
            background = rounded(blue, 16)
            setOnClickListener { click() }
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
