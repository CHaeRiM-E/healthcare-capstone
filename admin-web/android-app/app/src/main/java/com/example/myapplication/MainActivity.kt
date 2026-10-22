package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val editEmail =
            findViewById<EditText>(R.id.editId)

        val editPw =
            findViewById<EditText>(R.id.editPw)

        val btnLogin =
            findViewById<Button>(R.id.btnLogin)

        val btnSignUp =
            findViewById<TextView>(R.id.btnSignUp)

        val errorText =
            findViewById<TextView>(R.id.errorText)

        btnSignUp.setOnClickListener {
            val intent =
                Intent(this, SignUpActivity::class.java)

            startActivity(intent)
        }

        btnLogin.setOnClickListener {

            val email =
                editEmail.text.toString().trim()

            val password =
                editPw.text.toString()

            when {

                email.isEmpty() || password.isEmpty() -> {
                    errorText.text =
                        "이메일과 비밀번호를 입력해주세요."
                }

                !Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches() -> {

                    errorText.text =
                        "올바른 이메일 주소를 입력해주세요."
                }

                else -> {

                    errorText.text = ""
                    btnLogin.isEnabled = false

                    auth.signInWithEmailAndPassword(
                        email,
                        password
                    ).addOnCompleteListener { task ->

                        btnLogin.isEnabled = true

                        if (task.isSuccessful) {

                            val intent =
                                Intent(
                                    this,
                                    HomeActivity::class.java
                                )

                            startActivity(intent)
                            finish()

                        } else {

                            val errorMessage =
                                task.exception?.localizedMessage
                                    ?: "알 수 없는 오류"

                            errorText.text = errorMessage

                            android.util.Log.e(
                                "LOGIN_ERROR",
                                "Firebase 로그인 실패",
                                task.exception
                            )
                        }
                    }
                }
            }
        }
    }
}