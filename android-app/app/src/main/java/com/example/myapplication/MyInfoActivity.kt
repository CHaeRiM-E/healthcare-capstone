package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyInfoActivity : AppCompatActivity() {

    private lateinit var txtAllergy: TextView
    private lateinit var txtDisease: TextView
    private lateinit var txtMedicine: TextView
    private lateinit var txtPrescriptionMedicines: TextView
    private lateinit var txtGuardian: TextView
    private lateinit var txtEmergencyContact: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_my_info)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        txtAllergy = findViewById(R.id.txtAllergy)
        txtDisease = findViewById(R.id.txtDisease)
        txtMedicine = findViewById(R.id.txtMedicine)
        // 기존 직접 입력 약물 아래에 처방약 자동 연동 영역을 추가합니다.
        txtPrescriptionMedicines = TextView(this).apply {
            text = "처방전에서 복용 중인 약물 : 불러오는 중..."
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#111111"))
            val margin = (12 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = margin }
        }
        (txtMedicine.parent as LinearLayout).apply {
            addView(txtPrescriptionMedicines, indexOfChild(txtMedicine) + 1)
        }
        txtGuardian = findViewById(R.id.txtGuardian)
        txtEmergencyContact =
            findViewById(R.id.txtEmergencyContact)

        val btnHome =
            findViewById<Button>(R.id.btnHome)

        val btnEditHealth =
            findViewById<Button>(R.id.btnEditHealth)

        val btnChangeGuardian =
            findViewById<Button>(R.id.btnChangeGuardian)

        val btnLoginInfo =
            findViewById<Button>(R.id.btnLoginInfo)

        loadInfo()

        btnHome.setOnClickListener {
            finish()
        }

        btnEditHealth.setOnClickListener {
            val intent = Intent(
                this,
                EditHealthActivity::class.java
            )

            startActivity(intent)
        }

        btnChangeGuardian.setOnClickListener {
            showGuardianEditDialog()
        }

        btnLoginInfo.setOnClickListener {
            val user = auth.currentUser

            if (user == null) {
                Toast.makeText(
                    this,
                    "로그인 정보가 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "로그인 이메일 : ${user.email}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        loadInfo()
        loadPrescriptionMedicines()
    }

    private fun loadInfo() {

        val user = auth.currentUser

        if (user == null) {
            Toast.makeText(
                this,
                "로그인 정보가 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    Toast.makeText(
                        this,
                        "사용자 정보를 찾을 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                val allergy =
                    document.getString("allergy") ?: ""

                val disease =
                    document.getString("disease") ?: ""

                val medicine =
                    document.getString("medicine") ?: ""

                val guardianName =
                    document.getString("guardianName") ?: ""

                val guardianRelation =
                    document.getString("guardianRelation") ?: ""

                val guardianPhone =
                    document.getString("guardianPhone") ?: ""

                val emergencyContactEnabled =
                    document.getBoolean(
                        "emergencyContactEnabled"
                    ) ?: false

                txtAllergy.text =
                    "알레르기 : ${
                        allergy.ifBlank {
                            "등록된 정보 없음"
                        }
                    }"

                txtDisease.text =
                    "지병 : ${
                        disease.ifBlank {
                            "등록된 정보 없음"
                        }
                    }"

                txtMedicine.text =
                    "복용 중인 약물 : ${
                        medicine.ifBlank {
                            "등록된 정보 없음"
                        }
                    }"

                txtGuardian.text =
                    if (guardianPhone.isBlank()) {

                        "등록된 보호자가 없습니다."

                    } else {

                        val nameText =
                            guardianName.ifBlank {
                                "이름 미등록"
                            }

                        val relationText =
                            guardianRelation.ifBlank {
                                "관계 미등록"
                            }

                        """
                        보호자 : $nameText ($relationText)
                        연락처 : $guardianPhone
                        """.trimIndent()
                    }

                txtEmergencyContact.text =
                    if (emergencyContactEnabled) {
                        "긴급 상황 연락 사용 중"
                    } else {
                        "긴급 상황 연락 사용 안 함"
                    }
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "사용자 정보를 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun loadPrescriptionMedicines() {
        val user = auth.currentUser
        if (user == null) {
            txtPrescriptionMedicines.text = "처방전에서 복용 중인 약물 : 로그인 필요"
            return
        }

        txtPrescriptionMedicines.text = "처방전에서 복용 중인 약물 : 불러오는 중..."
        db.collection("users")
            .document(user.uid)
            .collection("medicineRecords")
            .get()
            .addOnSuccessListener { records ->
                val activeNames = records.flatMap { record ->
                    val items = record.get("medicines") as? List<*> ?: emptyList<Any>()
                    items.mapNotNull { raw ->
                        val item = raw as? Map<*, *> ?: return@mapNotNull null
                        val status = item["status"] as? String ?: "확인 필요"
                        val name = item["medicineName"] as? String
                        name?.takeIf { it.isNotBlank() && status == "복용 중" }
                    }
                }.distinct()

                txtPrescriptionMedicines.text = if (activeNames.isEmpty()) {
                    "처방전에서 복용 중인 약물 : 없음"
                } else {
                    "처방전에서 복용 중인 약물 :\n" +
                            activeNames.joinToString("\n") { "• $it" }
                }
            }
            .addOnFailureListener {
                txtPrescriptionMedicines.text = "처방전에서 복용 중인 약물 : 불러오기 실패"
            }
    }

    private fun showGuardianEditDialog() {

        val user = auth.currentUser

        if (user == null) {
            Toast.makeText(
                this,
                "로그인 정보가 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                val padding = dpToPx(22)

                setPadding(
                    padding,
                    dpToPx(8),
                    padding,
                    0
                )
            }

        val editName =
            EditText(this).apply {

                hint = "보호자 이름"
            }

        val editRelation =
            EditText(this).apply {

                hint = "관계 예: 부모님, 언니"
            }

        val editPhone =
            EditText(this).apply {

                hint = "보호자 전화번호"

                inputType =
                    InputType.TYPE_CLASS_PHONE
            }

        val checkEmergency =
            CheckBox(this).apply {

                text =
                    "응급 상황 시 보호자 연락 기능 사용"
            }

        container.addView(editName)
        container.addView(editRelation)
        container.addView(editPhone)
        container.addView(checkEmergency)

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->

                editName.setText(
                    document.getString(
                        "guardianName"
                    ) ?: ""
                )

                editRelation.setText(
                    document.getString(
                        "guardianRelation"
                    ) ?: ""
                )

                editPhone.setText(
                    document.getString(
                        "guardianPhone"
                    ) ?: ""
                )

                checkEmergency.isChecked =
                    document.getBoolean(
                        "emergencyContactEnabled"
                    ) ?: false
            }

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("보호자 정보")
                .setView(container)
                .setPositiveButton(
                    "저장",
                    null
                )
                .setNegativeButton(
                    "취소",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val name =
                    editName.text
                        .toString()
                        .trim()

                val relation =
                    editRelation.text
                        .toString()
                        .trim()

                val phone =
                    editPhone.text
                        .toString()
                        .replace(" ", "")
                        .replace("-", "")
                        .trim()

                if (
                    name.isBlank() ||
                    relation.isBlank() ||
                    phone.isBlank()
                ) {

                    Toast.makeText(
                        this,
                        "보호자 정보를 모두 입력해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                if (
                    !phone.matches(
                        Regex(
                            "^01[016789][0-9]{7,8}$"
                        )
                    )
                ) {

                    Toast.makeText(
                        this,
                        "올바른 휴대전화 번호를 입력해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                val guardianData =
                    hashMapOf<String, Any>(
                        "guardianName" to name,
                        "guardianRelation" to relation,
                        "guardianPhone" to
                                formatPhoneNumber(phone),
                        "emergencyContactEnabled" to
                                checkEmergency.isChecked
                    )

                db.collection("users")
                    .document(user.uid)
                    .update(guardianData)
                    .addOnSuccessListener {

                        Toast.makeText(
                            this,
                            "보호자 정보가 저장되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()

                        loadInfo()

                        dialog.dismiss()
                    }
                    .addOnFailureListener {

                        Toast.makeText(
                            this,
                            "보호자 정보 저장에 실패했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }

        dialog.show()
    }

    private fun formatPhoneNumber(
        phone: String
    ): String {

        return when (phone.length) {

            10 -> {
                "${phone.substring(0, 3)}-" +
                        "${phone.substring(3, 6)}-" +
                        phone.substring(6)
            }

            11 -> {
                "${phone.substring(0, 3)}-" +
                        "${phone.substring(3, 7)}-" +
                        phone.substring(7)
            }

            else -> phone
        }
    }

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
                dp *
                        resources.displayMetrics.density
                ).toInt()
    }
}

