package com.example.myapplication

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditHealthActivity : AppCompatActivity() {

    private lateinit var txtEditAllergy: TextView
    private lateinit var txtEditDisease: TextView
    private lateinit var txtEditMedicine: TextView

    private lateinit var keywordPanel: LinearLayout
    private lateinit var txtKeywordTitle: TextView
    private lateinit var txtSelectionGuide: TextView
    private lateinit var searchKeyword: EditText
    private lateinit var keywordGrid: GridLayout

    private val selectedAllergies = linkedSetOf<String>()
    private val selectedDiseases = linkedSetOf<String>()
    private val selectedMedicines = linkedSetOf<String>()

    private var target = Target.DISEASE

    private enum class Target {
        ALLERGY,
        DISEASE,
        MEDICINE
    }

    // 자주 선택하는 알레르기 목록
    private val allergyKeywords = listOf(
        "고양이 알레르기",
        "개 알레르기",
        "집먼지진드기",
        "꽃가루 알레르기",
        "곰팡이 알레르기",
        "땅콩 알레르기",
        "견과류 알레르기",
        "우유 알레르기",
        "달걀 알레르기",
        "갑각류 알레르기",
        "생선 알레르기",
        "밀 알레르기",
        "대두 알레르기",
        "페니실린 알레르기",
        "아스피린 알레르기",
        "라텍스 알레르기"
    )

    // 자주 선택하는 지병 목록
    private val diseaseKeywords = listOf(
        "당뇨병",
        "고혈압",
        "저혈압",
        "천식",
        "간질",
        "심장질환",
        "심근경색",
        "협심증",
        "부정맥",
        "뇌전증",
        "편두통",
        "빈혈",
        "혈액응고장애",
        "갑상선질환",
        "신장질환",
        "간질환",
        "지방간",
        "위궤양",
        "대장염",
        "아토피",
        "치매",
        "기면증",
        "골다공증",
        "류마티스관절염",
        "암 치료 중",
        "면역저하 질환"
    )

    // 자주 검색할 수 있는 약물 예시
    private val medicineKeywords = listOf(
        "타이레놀",
        "아스피린",
        "이부프로펜",
        "덱시부프로펜",
        "나프록센",
        "게보린",
        "판콜",
        "판피린",
        "지르텍",
        "알레그라",
        "클라리틴",
        "항생제",
        "항히스타민제",
        "스테로이드제",
        "피임약",
        "인슐린",
        "혈압약",
        "당뇨약",
        "갑상선약",
        "항응고제",
        "수면제",
        "항우울제",
        "진통제",
        "철분제",
        "비타민제"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_edit_health)

        txtEditAllergy =
            findViewById(R.id.txtEditAllergy)

        txtEditDisease =
            findViewById(R.id.txtEditDisease)

        txtEditMedicine =
            findViewById(R.id.txtEditMedicine)

        keywordPanel =
            findViewById(R.id.keywordPanel)

        txtKeywordTitle =
            findViewById(R.id.txtKeywordTitle)

        txtSelectionGuide =
            findViewById(R.id.txtSelectionGuide)

        searchKeyword =
            findViewById(R.id.searchKeyword)

        keywordGrid =
            findViewById(R.id.keywordGrid)

        val btnHome =
            findViewById<Button>(R.id.btnHome)

        val btnAddAllergy =
            findViewById<Button>(R.id.btnAddAllergy)

        val btnAddDisease =
            findViewById<Button>(R.id.btnAddDisease)

        val btnAddMedicine =
            findViewById<Button>(R.id.btnAddMedicine)

        val btnAddCustomKeyword =
            findViewById<Button>(R.id.btnAddCustomKeyword)

        val btnClearSelection =
            findViewById<Button>(R.id.btnClearSelection)

        val btnCloseKeyword =
            findViewById<Button>(R.id.btnCloseKeyword)

        val btnSaveHealth =
            findViewById<Button>(R.id.btnSaveHealth)

        // Firebase에서 현재 로그인한 사용자의 건강정보 불러오기
        loadSavedHealthInfo()

        keywordPanel.visibility = View.GONE

        btnHome.setOnClickListener {
            finish()
        }

        btnAddAllergy.setOnClickListener {
            showKeywords(Target.ALLERGY)
        }

        btnAddDisease.setOnClickListener {
            showKeywords(Target.DISEASE)
        }

        btnAddMedicine.setOnClickListener {
            showKeywords(Target.MEDICINE)
        }

        // 직접 입력한 항목 추가
        btnAddCustomKeyword.setOnClickListener {

            val customKeyword =
                searchKeyword.text.toString().trim()

            if (customKeyword.isBlank()) {

                Toast.makeText(
                    this,
                    "추가할 내용을 입력해주세요.",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                addCustomKeyword(customKeyword)

                searchKeyword.setText("")
            }
        }

        // 현재 선택 항목 전체 삭제
        btnClearSelection.setOnClickListener {

            currentSelection().clear()

            updateSelectedTexts()
            renderKeywords(searchKeyword.text.toString())

            Toast.makeText(
                this,
                "현재 항목의 선택 내용이 삭제되었습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnCloseKeyword.setOnClickListener {
            keywordPanel.visibility = View.GONE
        }

        // 입력할 때마다 목록 검색
        searchKeyword.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                    // 사용하지 않음
                }

                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    renderKeywords(
                        text?.toString() ?: ""
                    )
                }

                override fun afterTextChanged(
                    text: Editable?
                ) {
                    // 사용하지 않음
                }
            }
        )

        btnSaveHealth.setOnClickListener {
            saveHealthInfo()
        }
    }

    private fun loadSavedHealthInfo() {

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            updateSelectedTexts()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->

                selectedAllergies.clear()
                selectedDiseases.clear()
                selectedMedicines.clear()

                val allergy =
                    document.getString("allergy") ?: ""

                val disease =
                    document.getString("disease") ?: ""

                val medicine =
                    document.getString("medicine") ?: ""

                selectedAllergies.addAll(
                    parseSavedText(allergy)
                )

                selectedDiseases.addAll(
                    parseSavedText(disease)
                )

                selectedMedicines.addAll(
                    parseSavedText(medicine)
                )

                updateSelectedTexts()
            }
            .addOnFailureListener {

                updateSelectedTexts()

                Toast.makeText(
                    this,
                    "건강정보를 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun parseSavedText(
        savedText: String
    ): List<String> {

        if (savedText.isBlank()) {
            return emptyList()
        }

        return savedText
            .split(",")
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                        it != "없음" &&
                        it != "등록된 정보 없음"
            }
    }

    private fun showKeywords(
        selectedTarget: Target
    ) {

        target = selectedTarget
        keywordPanel.visibility = View.VISIBLE
        searchKeyword.setText("")

        when (target) {

            Target.ALLERGY -> {
                txtKeywordTitle.text =
                    "알레르기 선택"

                searchKeyword.hint =
                    "알레르기를 검색하거나 직접 입력"
            }

            Target.DISEASE -> {
                txtKeywordTitle.text =
                    "지병 선택"

                searchKeyword.hint =
                    "지병을 검색하거나 직접 입력"
            }

            Target.MEDICINE -> {
                txtKeywordTitle.text =
                    "복용 중인 약물 선택"

                searchKeyword.hint =
                    "약 이름을 검색하거나 직접 입력"
            }
        }

        txtSelectionGuide.text =
            "선택된 항목을 다시 누르면 삭제됩니다."

        renderKeywords("")
    }

    private fun renderKeywords(
        query: String
    ) {

        keywordGrid.removeAllViews()

        val keywords =
            currentKeywords().filter {

                it.contains(
                    query.trim(),
                    ignoreCase = true
                )
            }

        if (keywords.isEmpty()) {

            val emptyText =
                TextView(this).apply {

                    text =
                        if (query.isBlank()) {
                            "표시할 항목이 없습니다."
                        } else {
                            "검색 결과가 없습니다.\n직접 추가 버튼을 이용해주세요."
                        }

                    textSize = 15f
                    setTextColor(Color.GRAY)
                    setPadding(16, 25, 16, 25)
                }

            keywordGrid.addView(emptyText)

            return
        }

        for (word in keywords) {

            val button =
                Button(this).apply {

                    text = word
                    textSize = 13f
                    isAllCaps = false

                    val selected =
                        currentSelection().contains(word)

                    if (selected) {

                        backgroundTintList =
                            ColorStateList.valueOf(
                                Color.parseColor("#5F86F2")
                            )

                        setTextColor(Color.WHITE)

                    } else {

                        backgroundTintList =
                            ColorStateList.valueOf(
                                Color.parseColor("#E4E7EC")
                            )

                        setTextColor(
                            Color.parseColor("#222222")
                        )
                    }

                    setOnClickListener {
                        toggleKeyword(word)
                    }
                }

            val params =
                GridLayout.LayoutParams().apply {

                    width = 0
                    height =
                        GridLayout.LayoutParams.WRAP_CONTENT

                    columnSpec =
                        GridLayout.spec(
                            GridLayout.UNDEFINED,
                            1f
                        )

                    setMargins(
                        5,
                        5,
                        5,
                        5
                    )
                }

            button.layoutParams = params
            keywordGrid.addView(button)
        }
    }

    private fun toggleKeyword(
        word: String
    ) {

        val selection = currentSelection()

        if (selection.contains(word)) {

            selection.remove(word)

        } else {

            selection.add(word)
        }

        updateSelectedTexts()
        renderKeywords(searchKeyword.text.toString())
    }

    private fun addCustomKeyword(
        word: String
    ) {

        val selection = currentSelection()

        if (selection.contains(word)) {

            Toast.makeText(
                this,
                "이미 선택한 항목입니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        selection.add(word)

        updateSelectedTexts()
        renderKeywords("")

        Toast.makeText(
            this,
            "$word 항목이 추가되었습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun currentSelection():
            LinkedHashSet<String> {

        return when (target) {

            Target.ALLERGY ->
                selectedAllergies

            Target.DISEASE ->
                selectedDiseases

            Target.MEDICINE ->
                selectedMedicines
        }
    }

    private fun currentKeywords():
            List<String> {

        return when (target) {

            Target.ALLERGY ->
                allergyKeywords

            Target.DISEASE ->
                diseaseKeywords

            Target.MEDICINE ->
                medicineKeywords
        }
    }

    private fun updateSelectedTexts() {

        txtEditAllergy.text =
            "알레르기\n${displayText(selectedAllergies)}"

        txtEditDisease.text =
            "지병\n${displayText(selectedDiseases)}"

        txtEditMedicine.text =
            "복용 중인 약물\n${displayText(selectedMedicines)}"
    }

    private fun displayText(
        values: Set<String>
    ): String {

        return if (values.isEmpty()) {
            "등록된 정보 없음"
        } else {
            values.joinToString(", ")
        }
    }

    private fun saveHealthInfo() {

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {

            Toast.makeText(
                this,
                "로그인 정보가 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val healthData = mapOf(
            "allergy" to
                    selectedAllergies.joinToString(", "),
            "disease" to
                    selectedDiseases.joinToString(", "),
            "medicine" to
                    selectedMedicines.joinToString(", ")
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .update(healthData)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "건강정보가 저장되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "건강정보 저장에 실패했습ㅛ니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}