package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.EditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.ImageView
import android.view.View
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class ErrorReportActivity : AppCompatActivity() {
    private var selectedImageUri: Uri? = null

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri

            val imgPreview = findViewById<ImageView>(
                R.id.imgScreenshotPreview
            )

            imgPreview.setImageURI(uri)
            imgPreview.visibility = View.VISIBLE

            Toast.makeText(
                this,
                "스크린샷이 선택되었습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_error_report)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnHome = findViewById<Button>(R.id.btnHome)
        val btnUpload = findViewById<Button>(R.id.btnUpload)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val editType = findViewById<Spinner>(R.id.editType)

        val errorTypes = listOf(
            "오류 유형을 선택해주세요",
            "분석 결과 오류",
            "사진 업로드 오류",
            "앱 실행 오류",
            "기타"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            errorTypes
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        editType.adapter = adapter

        btnBack.setOnClickListener {
            finish()
        }

        btnHome.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
        }

        btnUpload.setOnClickListener {
            imagePicker.launch("image/*")
        }

        btnSubmit.setOnClickListener {

            val errorType = editType.selectedItem.toString()

            val errorContent = findViewById<EditText>(
                R.id.edtErrorContent
            ).text.toString().trim()

            if (editType.selectedItemPosition == 0) {
                Toast.makeText(
                    this,
                    "오류 유형을 선택해주세요.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (errorContent.isEmpty()) {
                Toast.makeText(
                    this,
                    "오류 내용을 입력해주세요.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val report = hashMapOf(
                "userId" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
                "errorType" to errorType,
                "errorContent" to errorContent,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            val screenshotUri = selectedImageUri

            if (screenshotUri != null) {
                btnSubmit.isEnabled = false

                val userId = FirebaseAuth.getInstance().currentUser?.uid

                if (userId == null) {
                    btnSubmit.isEnabled = true
                    Toast.makeText(
                        this,
                        "로그인 후 신고해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val fileName = "${UUID.randomUUID()}.jpg"

                val storageRef = FirebaseStorage.getInstance()
                    .reference
                    .child("errorReports/$userId/$fileName")

                storageRef.putFile(screenshotUri)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) {
                            throw task.exception
                                ?: Exception("사진 업로드 실패")
                        }
                        storageRef.downloadUrl
                    }
                    .addOnSuccessListener { downloadUri ->
                        report["screenshotUrl"] = downloadUri.toString()

                        FirebaseFirestore.getInstance()
                            .collection("errorReports")
                            .add(report)
                            .addOnSuccessListener {
                                btnSubmit.isEnabled = true

                                Toast.makeText(
                                    this,
                                    "오류 신고가 정상적으로 제출되었습니다.",
                                    Toast.LENGTH_LONG
                                ).show()

                                finish()
                            }
                            .addOnFailureListener { e ->

                                storageRef.delete()
                                    .addOnCompleteListener { deleteTask ->

                                        btnSubmit.isEnabled = true

                                        val message = if (deleteTask.isSuccessful) {
                                            "신고 저장에 실패했습니다. 첨부 사진은 정리되었습니다."
                                        } else {
                                            "신고 저장에 실패했습니다. 첨부 사진 정리도 실패했습니다."
                                        }

                                        Toast.makeText(
                                            this,
                                            "$message\n${e.localizedMessage}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                    }
                    .addOnFailureListener { e ->
                        btnSubmit.isEnabled = true

                        Toast.makeText(
                            this,
                            "사진 업로드 실패: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                return@setOnClickListener
            }

            btnSubmit.isEnabled = false

            FirebaseFirestore.getInstance()
                .collection("errorReports")
                .add(report)
                .addOnSuccessListener {
                    btnSubmit.isEnabled = true

                    Toast.makeText(
                        this,
                        "오류 신고가 정상적으로 제출되었습니다.",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                }
                .addOnFailureListener { e ->
                    btnSubmit.isEnabled = true

                    Toast.makeText(
                        this,
                        "신고 제출 실패: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}