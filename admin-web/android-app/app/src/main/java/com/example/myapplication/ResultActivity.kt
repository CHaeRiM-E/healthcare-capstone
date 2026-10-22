package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.Locale
import java.util.UUID
import android.widget.ImageView


class ResultActivity : AppCompatActivity() {

    private lateinit var txtResultName: TextView
    private lateinit var txtConfidence: TextView
    private lateinit var txtRisk: TextView
    private lateinit var txtEmergency: TextView
    private lateinit var txtFirstAid: TextView
    private lateinit var categoryBox: LinearLayout

    // 문진을 완료했을 때 저장되는 위험 수준
    private var triageRisk: String? = null

    // 직접 카테고리를 변경했는지 확인
    private var categoryChangedByUser = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val btnHome = findViewById<Button>(R.id.btnHome)
        val btnRetake = findViewById<Button>(R.id.btnRetake)
        val btnChangeCategory =
            findViewById<Button>(R.id.btnChangeCategory)

        val btnTriage =
            findViewById<Button>(R.id.btnTriage)

        val btnSaveRecord =
            findViewById<Button>(R.id.btnSaveRecord)

        val btnFindHospital =
            findViewById<Button>(R.id.btnFindHospital)

        val btnReportError =
            findViewById<Button>(R.id.btnReportError)

        txtResultName =
            findViewById(R.id.txtResultName)

        txtConfidence =
            findViewById(R.id.txtConfidence)

        txtRisk =
            findViewById(R.id.txtRisk)

        txtEmergency =
            findViewById(R.id.txtEmergency)

        txtFirstAid =
            findViewById(R.id.txtFirstAid)

        categoryBox =
            findViewById(R.id.categoryBox)

        // =========================
        // AI 분석 결과 받아오기
        // =========================

        val injuryName =
            intent.getStringExtra("injuryName")
                ?: "분석 결과 없음"

        val confidence =
            intent.getDoubleExtra(
                "confidence",
                0.0
            )

        // 분석에 사용한 사진 URI
        val imageUri =
            intent.getStringExtra("imageUri")
        val imgWound = findViewById<ImageView>(R.id.imgWound)

        if (!imageUri.isNullOrBlank()) {
            imgWound.setImageURI(Uri.parse(imageUri))
        }

        // 상처 종류와 응급처치 표시
        updateResult(injuryName)

        // 응급도는 문진 후 결정
        resetTriage()

        // AI 신뢰도 표시

        // AI 탐지 점수 표시
        if (confidence > 0.0) {
            txtConfidence.text =
                String.format(
                    Locale.KOREA,
                    "AI 탐지 점수 : %.1f%%",
                    confidence
                )
        } else {
            txtConfidence.text =
                "AI 탐지 점수 : -"
        }


        // 기존 XML 변경 없이 외상 분석 결과에서 AI 추가 상담으로 연결
        val btnAiFollowUp = Button(this).apply {
            text = "💬 AI에게 추가 상담하기"
            isAllCaps = false
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#3A86FF")
            )
            setOnClickListener {
                val currentInjury = txtResultName.text.toString()
                val score = if (categoryChangedByUser) -1.0 else confidence
                startActivity(Intent(this@ResultActivity, InternalChatActivity::class.java).apply {
                    putExtra("fromInjuryResult", true)
                    putExtra("injuryName", currentInjury)
                    putExtra("confidence", score)
                    // 이미지 파일/URI는 챗봇 API에 전달하지 않음
                })
            }
        }
        val anchor = btnTriage
        val parent = anchor.parent as? android.view.ViewGroup
        if (parent != null) {
            val index = parent.indexOfChild(anchor)
            if (parent is LinearLayout) {
                parent.addView(
                    btnAiFollowUp, index + 1,
                    LinearLayout.LayoutParams(-1, (52 * resources.displayMetrics.density).toInt())
                        .apply {
                            topMargin = (8 * resources.displayMetrics.density).toInt()
                        })
            } else {
                parent.addView(btnAiFollowUp, index + 1)
            }
        }

        // =========================
        // 응급도 확인
        // =========================

        btnTriage.setOnClickListener {
            startTriage(
                txtResultName.text.toString()
            )
        }

        // =========================
        // 홈
        // =========================

        btnHome.setOnClickListener {

            val homeIntent =
                Intent(
                    this,
                    HomeActivity::class.java
                )

            homeIntent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP

            startActivity(homeIntent)
            finish()
        }

        // =========================
        // 재촬영
        // =========================

        btnRetake.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    CameraActivity::class.java
                )
            )

            finish()
        }

        // =========================
        // 카테고리 재설정 열기/닫기
        // =========================

        btnChangeCategory.setOnClickListener {

            categoryBox.visibility =
                if (categoryBox.visibility == View.VISIBLE) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }

        // =========================
        // 직접 카테고리 변경
        // =========================

        findViewById<TextView>(
            R.id.catScratch
        ).setOnClickListener {
            manualChange("찰과상")
        }

        findViewById<TextView>(
            R.id.catContusion
        ).setOnClickListener {
            manualChange("타박상")
        }

        findViewById<TextView>(
            R.id.catBurn
        ).setOnClickListener {
            manualChange("화상")
        }

        findViewById<TextView>(
            R.id.catCut
        ).setOnClickListener {
            manualChange("베인상처")
        }

        findViewById<TextView>(
            R.id.catBite
        ).setOnClickListener {
            manualChange("벌레물림")
        }

        // =========================
        // 분석 결과 기록 저장
        // =========================

        btnSaveRecord.setOnClickListener {

            val user = FirebaseAuth.getInstance().currentUser

            if (user == null) {
                Toast.makeText(
                    this,
                    "로그인 정보가 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (triageRisk == null) {
                Toast.makeText(
                    this,
                    "먼저 응급도 확인을 진행해주세요.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val currentInjuryName = txtResultName.text.toString()

            val confidenceToSave =
                if (categoryChangedByUser) 0.0 else confidence

            btnSaveRecord.isEnabled = false
            btnSaveRecord.text = "저장 중..."

            fun saveRecord(downloadUrl: String) {
                val recordData =
                    hashMapOf(
                        "injuryName" to currentInjuryName,
                        "confidence" to confidenceToSave,
                        "risk" to triageRisk,
                        "emergency" to txtEmergency.text.toString(),
                        "firstAid" to txtFirstAid.text.toString(),
                        "imageUri" to downloadUrl,
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.uid)
                    .collection("injuryRecords")
                    .add(recordData)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "분석 결과가 기록되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()

                        btnSaveRecord.text = "기록 완료"
                    }
                    .addOnFailureListener {
                        btnSaveRecord.isEnabled = true
                        btnSaveRecord.text = "결과 기록하기"

                        Toast.makeText(
                            this,
                            "기록 저장에 실패했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }

            if (imageUri.isNullOrBlank()) {
                saveRecord("")
            } else {
                try {
                    val localImageUri = Uri.parse(imageUri)
                    val fileName = "${UUID.randomUUID()}.jpg"

                    val storageRef =
                        FirebaseStorage.getInstance()
                            .reference
                            .child(
                                "users/${user.uid}/injuryImages/$fileName"
                            )

                    storageRef
                        .putFile(localImageUri)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) {
                                task.exception?.let { throw it }
                            }
                            storageRef.downloadUrl
                        }
                        .addOnSuccessListener { downloadUri ->
                            saveRecord(downloadUri.toString())
                        }
                        .addOnFailureListener {
                            btnSaveRecord.isEnabled = true
                            btnSaveRecord.text = "결과 기록하기"

                            Toast.makeText(
                                this,
                                "사진 업로드에 실패했습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                } catch (e: Exception) {
                    btnSaveRecord.isEnabled = true
                    btnSaveRecord.text = "결과 기록하기"

                    Toast.makeText(
                        this,
                        "사진을 불러오지 못했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // =========================
        // 가까운 병원 찾기
        // =========================

        btnFindHospital.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    HospitalActivity::class.java
                )
            )
        }

        // =========================
        // 오류 신고
        // =========================

        btnReportError.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    ErrorReportActivity::class.java
                )
            )
        }
    }

    // =========================
    // 응급도 초기화
    // =========================

    private fun resetTriage() {

        triageRisk = null

        txtRisk.text =
            "위험 수준 : 문진 전"

        txtEmergency.text =
            "응급도 확인을 진행해주세요."
    }

    // =========================
    // 응급도 문진
    // =========================

    private fun startTriage(
        injuryName: String
    ) {

        data class TriageQuestion(
            val text: String,
            val level: String
        )

        val questions = when (injuryName) {

            "찰과상" -> listOf(
                TriageQuestion(
                    "압박해도 출혈이 멈추지 않나요?",
                    "높음"
                ),
                TriageQuestion(
                    "상처가 매우 크거나 깊은가요?",
                    "높음"
                ),
                TriageQuestion(
                    "상처에 제거하기 어려운 이물질이 박혀 있나요?",
                    "높음"
                ),
                TriageQuestion(
                    "상처가 붓고 붉어지거나 고름이 나오나요?",
                    "중간"
                )
            )

            "타박상" -> listOf(
                TriageQuestion(
                    "통증이 매우 심하거나 점점 심해지고 있나요?",
                    "중간"
                ),
                TriageQuestion(
                    "다친 부위를 움직이거나 사용하기 매우 어려운가요?",
                    "중간"
                ),
                TriageQuestion(
                    "붓기나 멍이 매우 심하거나 빠르게 커지고 있나요?",
                    "중간"
                ),
                TriageQuestion(
                    "다친 부위의 감각이 없거나 저린 증상이 있나요?",
                    "높음"
                )
            )

            "화상" -> listOf(
                TriageQuestion(
                    "화상 부위가 매우 크거나 깊은가요?",
                    "높음"
                ),
                TriageQuestion(
                    "피부가 하얗거나 검게 변했나요?",
                    "높음"
                ),
                TriageQuestion(
                    "얼굴이나 생식기 부위에 화상을 입었나요?",
                    "높음"
                ),
                TriageQuestion(
                    "화학물질이나 전기로 인해 생긴 화상인가요?",
                    "높음"
                )
            )

            "베인상처" -> listOf(
                TriageQuestion(
                    "압박해도 출혈이 멈추지 않나요?",
                    "높음"
                ),
                TriageQuestion(
                    "상처가 매우 크거나 깊게 벌어져 있나요?",
                    "높음"
                ),
                TriageQuestion(
                    "상처 주변의 감각이 없거나 움직이기 어려운가요?",
                    "높음"
                ),
                TriageQuestion(
                    "상처가 붓고 붉어지거나 고름이 나오나요?",
                    "중간"
                )
            )

            "벌레물림" -> listOf(
                TriageQuestion(
                    "숨쉬기 어렵거나 목이 조이는 느낌이 있나요?",
                    "높음"
                ),
                TriageQuestion(
                    "입술, 입, 혀 또는 목이 갑자기 붓고 있나요?",
                    "높음"
                ),
                TriageQuestion(
                    "심하게 어지럽거나 의식을 잃을 것 같은가요?",
                    "높음"
                ),
                TriageQuestion(
                    "증상이 계속 심해지거나 물린 부위가 심하게 붓고 있나요?",
                    "중간"
                )
            )

            else -> listOf(
                TriageQuestion(
                    "증상이 심하거나 빠르게 악화되고 있나요?",
                    "중간"
                )
            )
        }

        var questionIndex = 0
        var hasHighRisk = false
        var hasMediumRisk = false

        val selectedSymptoms =
            mutableListOf<String>()

        fun showQuestion() {

            if (questionIndex >= questions.size) {

                val risk = when {
                    hasHighRisk -> "높음"
                    hasMediumRisk -> "중간"
                    else -> "낮음"
                }

                triageRisk = risk
                txtRisk.text = "위험 수준 : $risk"

                txtEmergency.text =
                    when (risk) {
                        "높음" ->
                            "위험 신호가 확인되었습니다. 즉시 의료기관의 평가를 받으세요. " +
                                    "호흡곤란, 의식 저하, 심한 출혈 등이 있다면 119에 연락하세요."

                        "중간" ->
                            "주의가 필요한 증상이 확인되었습니다. 의료기관에서 상태를 확인하는 것을 권장합니다."

                        else ->
                            "현재 문진에서 뚜렷한 위험 신호가 확인되지 않았습니다. 증상 변화를 계속 관찰해주세요."
                    }

                val symptomMessage =
                    if (selectedSymptoms.isEmpty()) {
                        "선택된 위험 증상 없음"
                    } else {
                        "확인된 증상\n\n• " +
                                selectedSymptoms.joinToString("\n• ")
                    }

                AlertDialog.Builder(this)
                    .setTitle("응급도 확인 결과")
                    .setMessage(
                        "위험 수준 : $risk\n\n" +
                                symptomMessage +
                                "\n\n이 결과는 증상 확인을 위한 참고 정보이며 의료진의 진단을 대신하지 않습니다."
                    )
                    .setPositiveButton("확인", null)
                    .show()

                return
            }

            val currentQuestion =
                questions[questionIndex]

            AlertDialog.Builder(this)
                .setTitle(
                    "$injuryName 응급도 확인 " +
                            "(${questionIndex + 1}/${questions.size})"
                )
                .setMessage(currentQuestion.text)
                .setPositiveButton("예") { _, _ ->

                    selectedSymptoms.add(
                        currentQuestion.text
                    )
                    if (
                        injuryName == "벌레물림" &&
                        questionIndex <= 2
                    ) {
                        triageRisk = "높음"
                        txtRisk.text = "위험 수준 : 높음"
                        txtEmergency.text =
                            "응급 증상이 의심됩니다. 즉시 119에 연락하세요."

                        AlertDialog.Builder(this)
                            .setTitle("긴급 증상 안내")
                            .setMessage(
                                "심각한 알레르기 반응이 의심될 수 있습니다.\n\n" +
                                        "즉시 119에 연락하거나 응급진료를 받으세요."
                            )
                            .setPositiveButton("확인", null)
                            .show()

                        return@setPositiveButton
                    }

                    when (currentQuestion.level) {
                        "높음" -> hasHighRisk = true
                        "중간" -> hasMediumRisk = true
                    }

                    questionIndex++
                    showQuestion()
                }
                .setNegativeButton("아니오") { _, _ ->
                    questionIndex++
                    showQuestion()
                }
                .setCancelable(false)
                .show()
        }

        showQuestion()
    }

    // =========================
    // 사용자가 직접 카테고리 변경
    // =========================

    private fun manualChange(
        injuryName: String
    ) {

        updateResult(injuryName)

        categoryChangedByUser = true

        txtConfidence.text =
            "사용자가 직접 카테고리를 재설정했습니다."

        // 상처 종류가 바뀌었으므로
        // 이전 문진 결과는 무효
        resetTriage()

        categoryBox.visibility =
            View.GONE
    }

    // =========================
    // 상처 종류별 화면 변경
    // =========================

    private fun updateResult(
        injuryName: String
    ) {

        txtResultName.text =
            injuryName

        when (injuryName) {

            "찰과상" -> {

                txtFirstAid.text =
                    """
                    응급 처치 방법

                    • 흐르는 물로 상처를 깨끗이 씻기
                    • 이물질이 있다면 조심스럽게 제거
                    • 깨끗한 거즈나 밴드로 상처 보호
                    • 붓기, 심한 통증, 고름 등이 생기면 병원 방문
                    """.trimIndent()
            }

            "타박상" -> {

                txtFirstAid.text =
                    """
                    응급 처치 방법

                    • 초기에는 냉찜질
                    • 다친 부위를 충분히 쉬게 하기
                    • 가능하면 다친 부위를 심장보다 높게 유지
                    • 심한 통증이나 움직임 제한이 지속되면 병원 방문
                    """.trimIndent()
            }

            "화상" -> {

                txtFirstAid.text =
                    """
                    응급 처치 방법

                    • 흐르는 시원한 물로 화상 부위를 약 20분 식히기
                    • 얼음을 피부에 직접 대지 않기
                    • 물집을 일부러 터뜨리지 않기
                    • 깨끗한 거즈 등으로 느슨하게 보호
                    • 넓거나 깊은 화상은 의료기관 방문
                    """.trimIndent()
            }

            "베인상처" -> {

                txtFirstAid.text =
                    """
                    응급 처치 방법

                    • 깨끗한 거즈나 천으로 직접 압박해 지혈
                    • 출혈이 줄어들면 흐르는 물로 상처 세척
                    • 깨끗한 밴드나 거즈로 상처 보호
                    • 깊게 베였거나 출혈이 멈추지 않으면 병원 방문
                    """.trimIndent()
            }

            "벌레물림" -> {

                txtFirstAid.text =
                    """
                    응급 처치 방법

                    • 물린 부위를 비누와 물로 깨끗이 씻기
                    • 붓거나 가려우면 냉찜질
                    • 긁지 않도록 주의
                    • 호흡곤란, 심한 부종 등 전신 반응이 나타나면 즉시 응급진료
                    """.trimIndent()
            }

            else -> {

                txtFirstAid.text =
                    """
                    응급 처치 방법

                    • 상처 부위를 확인해주세요.
                    • 증상이 심하거나 지속되면 의료기관을 방문해주세요.
                    """.trimIndent()
            }
        }
    }
}
