package com.example.myapplication

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val editEmail =
            findViewById<EditText>(R.id.editSignUpId)

        val editPw =
            findViewById<EditText>(R.id.editSignUpPw)

        val editPwCheck =
            findViewById<EditText>(R.id.editSignUpPwCheck)

        val editName =
            findViewById<EditText>(R.id.editSignUpName)

        val errorText =
            findViewById<TextView>(R.id.signUpErrorText)

        val btnComplete =
            findViewById<Button>(R.id.btnCompleteSignUp)

        val btnBack =
            findViewById<TextView>(R.id.btnBackToLogin)

        btnComplete.setOnClickListener {

            val email =
                editEmail.text.toString().trim()

            val pw =
                editPw.text.toString()

            val pwCheck =
                editPwCheck.text.toString()

            val name =
                editName.text.toString().trim()

            when {

                email.isEmpty() ||
                        pw.isEmpty() ||
                        pwCheck.isEmpty() ||
                        name.isEmpty() -> {

                    errorText.text =
                        "모든 항목을 입력해주세요."
                }

                !Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches() -> {

                    errorText.text =
                        "올바른 이메일 주소를 입력해주세요."
                }

                pw.length < 6 -> {

                    errorText.text =
                        "비밀번호는 6자 이상 입력해주세요."
                }

                pw != pwCheck -> {

                    errorText.text =
                        "비밀번호가 일치하지 않습니다."
                }

                else -> {

                    errorText.text = ""
                    btnComplete.isEnabled = false

                    auth.createUserWithEmailAndPassword(
                        email,
                        pw
                    ).addOnCompleteListener { task ->

                        if (task.isSuccessful) {

                            val uid =
                                auth.currentUser?.uid

                            if (uid == null) {
                                btnComplete.isEnabled = true
                                errorText.text =
                                    "회원정보를 확인할 수 없습니다."
                                return@addOnCompleteListener
                            }

                            val userData = hashMapOf(
                                "name" to name,
                                "email" to email
                            )

                            db.collection("users")
                                .document(uid)
                                .set(userData)
                                .addOnSuccessListener {

                                    Toast.makeText(
                                        this,
                                        "회원가입이 완료되었습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    auth.signOut()
                                    finish()
                                }
                                .addOnFailureListener {

                                    btnComplete.isEnabled = true

                                    errorText.text =
                                        "회원정보 저장에 실패했습니다."
                                }

                        } else {

                            btnComplete.isEnabled = true

                            errorText.text =
                                task.exception?.localizedMessage
                                    ?: "회원가입에 실패했습니다."
                        }
                    }
                }
            }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}