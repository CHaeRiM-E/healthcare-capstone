package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AnalysisActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    // Firebase Cloud Functions 상처 분석 서버
    private val analysisUrl =
        "https://asia-northeast3-myapplication-b374c.cloudfunctions.net/analyzeInjury"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        val imageUriString = intent.getStringExtra("imageUri")

        if (imageUriString.isNullOrBlank()) {
            showAnalysisFailed()
            return
        }

        // Firebase 로그인 상태 확인
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            showAnalysisFailed(
                "로그인이 필요해요",
                "상처 분석 기능을 이용하려면 로그인해 주세요."
            )
            return
        }

        // Firebase ID 토큰 가져오기
        user.getIdToken(false)
            .addOnSuccessListener { result ->

                val idToken = result.token

                if (idToken.isNullOrBlank()) {
                    showAnalysisFailed(
                        "인증 오류",
                        "로그인 인증 정보를 가져오지 못했어요."
                    )
                    return@addOnSuccessListener
                }

                try {
                    val imageUri = Uri.parse(imageUriString)
                    val imageBytes = readImageBytes(imageUri)

                    if (imageBytes == null || imageBytes.isEmpty()) {
                        showAnalysisFailed()
                        return@addOnSuccessListener
                    }

                    sendImageToFirebase(imageBytes, idToken)

                } catch (e: Exception) {
                    Log.e("ANALYSIS", "이미지 처리 오류", e)
                    showAnalysisFailed()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ANALYSIS", "Firebase 인증 실패", e)

                showAnalysisFailed(
                    "로그인 인증 오류",
                    "인증 정보를 확인하지 못했어요. 다시 시도해 주세요."
                )
            }
    }

    private fun readImageBytes(uri: Uri): ByteArray? {
        return if (uri.scheme == "file") {
            val path = uri.path ?: return null
            File(path).readBytes()
        } else {
            contentResolver.openInputStream(uri)?.use {
                it.readBytes()
            }
        }
    }

    private fun sendImageToFirebase(
        imageBytes: ByteArray,
        idToken: String
    ) {

        val base64Image = Base64.encodeToString(
            imageBytes,
            Base64.NO_WRAP
        )

        val requestJson = JSONObject().apply {
            put("image", base64Image)
        }

        val requestBody = requestJson.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(analysisUrl)
            .addHeader(
                "Authorization",
                "Bearer $idToken"
            )
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("ANALYSIS", "Firebase 서버 연결 실패", e)

                runOnUiThread {
                    showAnalysisFailed(
                        "서버에 연결하지 못했어요",
                        "인터넷 연결 상태를 확인한 후 다시 촬영해 주세요."
                    )
                }
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {

                val responseCode = response.code

                val responseText = response.use {
                    it.body?.string().orEmpty()
                }

                if (!response.isSuccessful || responseText.isBlank()) {

                    Log.e(
                        "ANALYSIS",
                        "서버 오류: $responseCode, ${responseText.take(300)}"
                    )

                    runOnUiThread {
                        if (responseCode == 401) {
                            showAnalysisFailed(
                                "로그인 인증 오류",
                                "로그인 인증이 만료되었을 수 있어요. 다시 로그인해 주세요."
                            )
                        } else {
                            showAnalysisFailed(
                                "분석 서버 오류",
                                "상처 분석을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
                            )
                        }
                    }
                    return
                }

                try {
                    val json = JSONObject(responseText)

                    val success = json.optBoolean("success", false)

                    val injuryName = json.optString("injuryName", "")

                    val confidence = json.optDouble(
                        "confidence",
                        0.0
                    )

                    if (!success || injuryName.isBlank()) {
                        runOnUiThread {
                            showAnalysisFailed()
                        }
                        return
                    }

                    // 기존과 동일하게 탐지 점수 50% 미만 제외
                    if (!confidence.isFinite() || confidence < 50.0) {
                        runOnUiThread {
                            showAnalysisFailed()
                        }
                        return
                    }

                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            goToResult(
                                injuryName,
                                confidence
                            )
                        }
                    }

                } catch (e: Exception) {
                    Log.e("ANALYSIS", "분석 결과 해석 실패", e)

                    runOnUiThread {
                        showAnalysisFailed()
                    }
                }
            }
        })
    }

    private fun goToResult(
        injuryName: String,
        confidence: Double
    ) {

        val resultIntent = Intent(
            this,
            ResultActivity::class.java
        )

        resultIntent.putExtra("injuryName", injuryName)
        resultIntent.putExtra("confidence", confidence)
        resultIntent.putExtra(
            "imageUri",
            intent.getStringExtra("imageUri")
        )

        startActivity(resultIntent)
        finish()
    }

    private fun showAnalysisFailed(
        title: String = "상처를 인식하지 못했어요",
        message: String =
            "사진에서 분석 가능한 상처 종류를 확인하지 못했습니다.\n\n" +
                    "밝은 곳에서 상처 부위가 잘 보이도록 다시 촬영해 주세요.\n\n" +
                    "※ AI의 분류 결과는 의료 진단이 아닙니다."
    ) {

        if (isFinishing || isDestroyed) return

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("다시 촬영") { _, _ ->
                startActivity(
                    Intent(this, CameraActivity::class.java)
                )
                finish()
            }
            .setNegativeButton("홈으로") { _, _ ->
                val homeIntent = Intent(
                    this,
                    HomeActivity::class.java
                )

                homeIntent.flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

                startActivity(homeIntent)
                finish()
            }
            .show()
    }
}