package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MedicineActivity : AppCompatActivity() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()
    private val db = FirebaseFirestore.getInstance()
    private var photoUri: Uri? = null
    private var cameraBitmap: Bitmap? = null
    private var prescriptionDate = ""
    private var prescriptionDays = 0
    private var suggestedTimes = emptyList<String>()
    private var generation = 0
    private var pending = 0
    private val drugs = mutableListOf<Drug>()

    private data class Drug(
        val ocrName: String,
        var name: String,
        var itemSeq: String = "",
        var matched: Boolean = false,
        var efficacy: String = "",
        var aiEfficacy: String = "",
        var aiSideEffects: String = "",
        var aiPrecautions: String = "",
        var sideEffects: String = "",
        var precautions: String = "",
        var warnings: String = "",
        var usage: String = "",
        var times: List<String> = emptyList(),
        var loaded: Boolean = false,
        var candidateConfirmed: Boolean = false,
        var candidates: List<MedicineCandidate> = emptyList()
    )

    private data class MedicineCandidate(val name: String, val itemSeq: String)

    // 후보는 서버에서 실제 반환한 의약품만 사용하며 이름 유사도는 정렬에만 사용합니다.
    private fun editDistance(a: String, b: String): Int {
        val previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in b.indices) {
                val above = previous[j + 1]
                previous[j + 1] = minOf(previous[j + 1] + 1, previous[j] + 1,
                    diagonal + if (a[i] == b[j]) 0 else 1)
                diagonal = above
            }
        }
        return previous[b.length]
    }

    private fun shortInfo(raw: String, limit: Int = 150): String =
        raw.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ").trim().take(limit).ifBlank { "정보 없음" }

    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            photoUri = uri
            cameraBitmap = null
            findViewById<ImageView>(R.id.imgMedicinePreview).setImageURI(uri)
            resetResults()
        }
    }

    // 현재 카메라는 썸네일을 반환함. 작은 글씨 OCR은 갤러리 원본 사진을 권장.
    private val camera =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                @Suppress("DEPRECATION")
                val bitmap = result.data?.extras?.get("data") as? Bitmap
                if (bitmap != null) {
                    cameraBitmap = bitmap
                    photoUri = null
                    findViewById<ImageView>(R.id.imgMedicinePreview).setImageBitmap(bitmap)
                    resetResults()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine)
        findViewById<Button>(R.id.btnMedicineGallery).setOnClickListener { gallery.launch("image/*") }
        findViewById<Button>(R.id.btnMedicineCamera).setOnClickListener {
            camera.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        }
        findViewById<Button>(R.id.btnMedicineRecord).setOnClickListener {
            startActivity(Intent(this, MedicineRecordActivity::class.java))
        }
        findViewById<Button>(R.id.btnMedicineAnalyze).setOnClickListener { analyze() }
        findViewById<Button>(R.id.btnMedicineSave).setOnClickListener { confirmSave() }
        findViewById<Button>(R.id.btnMedicineAdd).setOnClickListener { editDrug(null) }
        val summary = findViewById<LinearLayout>(R.id.layoutMedicineSummary)
        val commonButton = Button(this).apply {
            text = "⏰ 전체 알림 시간 설정 · 한 번에 수정"
            isAllCaps = false
            setOnClickListener { editCommonTimes() }
        }
        summary.addView(commonButton)
        findViewById<Button>(R.id.btnToggleOcr).setOnClickListener {
            val t = findViewById<TextView>(R.id.txtMedicineResult)
            t.visibility = if (t.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun resetResults() {
        findViewById<Button>(R.id.btnMedicineAnalyze).isEnabled = true
        generation++
        drugs.clear()
        pending = 0
        findViewById<LinearLayout>(R.id.layoutMedicineSummary).visibility = View.GONE
        findViewById<LinearLayout>(R.id.layoutMedicineSearchCards).removeAllViews()
        findViewById<TextView>(R.id.txtMedicineSearchResult).visibility = View.GONE
        findViewById<Button>(R.id.btnMedicineSave).visibility = View.GONE
        findViewById<Button>(R.id.btnMedicineAdd).visibility = View.GONE
        findViewById<Button>(R.id.btnToggleOcr).visibility = View.GONE
    }

    // Gemini 서버에 약봉투 원본 이미지를 보내 분석합니다.
    private fun analyze() {
        if (photoUri == null && cameraBitmap == null) {
            toast("약봉투 사진을 먼저 선택해 주세요.")
            return
        }
        resetResults()
        val run = generation
        val status = findViewById<TextView>(R.id.txtMedicineSearchResult)
        status.visibility = View.VISIBLE
        status.text = "AI가 약봉투를 분석하고 있어요…"
        findViewById<Button>(R.id.btnMedicineAnalyze).isEnabled = false

        Thread {
            try {
                val bitmap = if (photoUri != null) {
                    contentResolver.openInputStream(photoUri!!)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: throw IOException("사진을 열 수 없습니다.")
                } else cameraBitmap ?: throw IOException("사진이 없습니다.")

                // 서버 이미지 제한을 지키면서 작은 글씨가 읽히도록 해상도를 유지
                val maxSide = maxOf(bitmap.width, bitmap.height)
                val scale = minOf(1.0, 1800.0 / maxSide)
                val resized = if (scale < 1.0) {
                    Bitmap.createScaledBitmap(
                        bitmap, (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt(), true
                    )
                } else bitmap
                val bytes = ByteArrayOutputStream().use { stream ->
                    resized.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    stream.toByteArray()
                }
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                if (encoded.length > 9500000) throw IOException("이미지가 너무 큽니다.")

                runOnUiThread {
                    if (run != generation) return@runOnUiThread
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        finishAiError(run, "로그인이 필요해요.")
                        return@runOnUiThread
                    }
                    user.getIdToken(false).addOnSuccessListener { tokenResult ->
                        if (run != generation) return@addOnSuccessListener
                        val token = tokenResult.token
                        if (token.isNullOrBlank()) {
                            finishAiError(run, "로그인 인증을 확인하지 못했어요.")
                            return@addOnSuccessListener
                        }
                        val payload = JSONObject()
                            .put("image", encoded)
                            .put("mimeType", "image/jpeg")
                        val request = Request.Builder()
                            .url("https://asia-northeast3-myapplication-b374c.cloudfunctions.net/analyzePrescription")
                            .header("Authorization", "Bearer $token")
                            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                        client.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                runOnUiThread { finishAiError(run, "AI 연결 실패: ${e.message}") }
                            }
                            override fun onResponse(call: Call, response: Response) {
                                val code = response.code
                                val body = response.use { it.body?.string().orEmpty() }
                                runOnUiThread {
                                    if (run != generation) return@runOnUiThread
                                    if (code !in 200..299) {
                                        finishAiError(run, "AI 분석 실패 (HTTP $code).")
                                        return@runOnUiThread
                                    }
                                    try {
                                        showAiResult(run, JSONObject(body))
                                    } catch (e: Exception) {
                                        finishAiError(run, "AI 응답을 해석하지 못했어요: ${e.message}")
                                    }
                                }
                            }
                        })
                    }.addOnFailureListener {
                        finishAiError(run, "로그인 인증 실패: ${it.message}")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { finishAiError(run, "사진 처리 실패: ${e.message}") }
            }
        }.start()
    }

    private fun finishAiError(run: Int, message: String) {
        if (run != generation) return
        findViewById<Button>(R.id.btnMedicineAnalyze).isEnabled = true
        findViewById<TextView>(R.id.txtMedicineSearchResult).apply {
            visibility = View.VISIBLE
            text = message
        }
    }

    private fun showAiResult(run: Int, result: JSONObject) {
        if (run != generation) return
        findViewById<Button>(R.id.btnMedicineAnalyze).isEnabled = true
        if (!result.optBoolean("success")) {
            finishAiError(run, "AI 분석 결과를 받지 못했어요.")
            return
        }

        val arr = result.optJSONArray("medicines")
        val rawNames = mutableListOf<String>()
        var extractedDays = 0
        var extractedFrequency = 0
        for (i in 0 until (arr?.length() ?: 0)) {
            val item = arr?.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            if (name.length < 2 || name in rawNames) continue
            rawNames.add(name)
            drugs.add(Drug(name, name).apply {
                // AI 설명은 공식 확인 정보와 별도로 보관
                aiEfficacy = item.optString("aiEfficacy").take(500)
                aiSideEffects = item.optString("aiSideEffects").take(500)
                aiPrecautions = item.optString("aiPrecautions").take(500)
                // 약별 복용 횟수가 명시된 경우에만 추천 알림 시간 표시
                val frequencyText = item.optString("frequency")
                val count = Regex("""(\d+)\s*회""").find(frequencyText)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                times = when (count) {
                    3 -> listOf("08:30", "13:30", "19:30")
                    2 -> listOf("08:30", "19:30")
                    1 -> listOf("09:00")
                    else -> emptyList()
                }
            })
            val days = item.optInt("days", 0)
            if (days in 1..365 && extractedDays == 0) extractedDays = days
            val frequency = item.optString("frequency")
            val count = Regex("""(\d+)\s*회""").find(frequency)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (count != null && count in 1..3 && extractedFrequency == 0) extractedFrequency = count
        }

        prescriptionDate = ""
        prescriptionDays = extractedDays
        suggestedTimes = when (extractedFrequency) {
            3 -> listOf("08:30", "13:30", "19:30")
            2 -> listOf("08:30", "19:30")
            1 -> listOf("09:00")
            else -> emptyList()
        }
        findViewById<TextView>(R.id.txtMedicineResult).text =
            "AI 인식 약품명 (저장 전 확인 필요)\\n" + rawNames.joinToString("\\n")
        findViewById<Button>(R.id.btnToggleOcr).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.layoutMedicineSummary).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnMedicineAdd).visibility = View.VISIBLE
        updateSummary()
        if (drugs.isEmpty()) {
            finishAiError(run, "AI가 약 이름을 찾지 못했어요. 약 추가로 직접 입력해 주세요.")
            render()
        } else {
            render()
            searchAll(run)
        }
    }

    private fun extractMedicineNames(raw: String): List<String> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val start = lines.indexOfFirst { it.replace(" ", "").contains("약품명") }
        val stopWords = listOf("복약안내", "복약안내사항", "복약안내약량", "[비스테로이드성", "[기타 진통제]", "조제약", "투약할 수 일수")
        val end = if (start >= 0) {
            (start + 1 until lines.size).firstOrNull { i -> stopWords.any { lines[i].contains(it) } } ?: lines.size
        } else lines.size
        val candidates = if (start >= 0) lines.subList(start + 1, end) else lines
        val excluded = listOf("약제비", "조제료", "본인부담", "영수증", "총액", "종액", "조제비", "약품비", "정제약", "투약", "원형", "타원형", "장방형", "필름코팅", "필름코", "흰색", "황색", "녹색", "투명", "향함", "향이", "장방", "제약", "병원", "환자", "복약", "보험", "진통제", "항생제", "소염제", "캡슐제", "색깔", "식전", "식후", "알약", "가글")
        val pattern = Regex("""[가-힣]{2,}(?:정|캡슐|시럽|현탁액|액|산)(?:\s*\d+(?:[.,]\d+)?\s*(?:mg|MG|밀리그램))?""")
        val result = mutableListOf<String>()
        for (line in candidates) {
            if (excluded.any { line.contains(it) }) continue
            // 제품명이 문장 중간에 있어도 인식하되, 용량·제형 설명은 제외합니다.
            pattern.findAll(line).forEach { match ->
                val name = match.value.replace(Regex("""\s+"""), "")
                if (name.length in 3..35 && excluded.none { name.contains(it) } && name !in result) result.add(name)
            }
        }
        return result.take(15)
    }

    private fun updateSummary() {
        findViewById<TextView>(R.id.txtMedicineSummary).text =
            "조제일: ${prescriptionDate.ifBlank { "확인 필요" }}\n복용 기간: ${if (prescriptionDays > 0) "${prescriptionDays}일" else "확인 필요"}\n※ 날짜와 복용법은 저장 전에 확인해 주세요."
    }

    private fun api(
        path: String,
        parameter: String,
        value: String,
        callback: (JSONObject?) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            callback(null); return
        }
        user.getIdToken(false).addOnSuccessListener { auth ->
            val token = auth.token
            if (token.isNullOrBlank()) {
                callback(null); return@addOnSuccessListener
            }
            val url =
                "https://asia-northeast3-myapplication-b374c.cloudfunctions.net/$path?$parameter=${
                    Uri.encode(value)
                }"
            val request =
                Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { callback(null) }
                }

                override fun onResponse(call: Call, response: Response) {
                    val json = response.use { r ->
                        val body = r.body?.string().orEmpty()

                        android.util.Log.d(
                            "MEDICINE_DEBUG",
                            "API=$path, HTTP=${r.code}, 응답=${body.take(1500)}"
                        )

                        if (!r.isSuccessful) {
                            null
                        } else {
                            try {
                                JSONObject(body)
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "MEDICINE_DEBUG",
                                    "JSON 분석 실패",
                                    e
                                )
                                null
                            }
                        }
                    }
                    runOnUiThread { callback(json) }
                }
            })
        }.addOnFailureListener { callback(null) }
    }

    private fun normalize(s: String): String =
        s.substringBefore("(")
            .substringBefore("（")
            .lowercase(Locale.ROOT)
            .replace(Regex("""[\s()\[\]{}·.,-]"""), "")

    private fun searchAll(run: Int) {
        pending = 0
        drugs.toList().forEach { drug -> searchDrug(drug, run) }
    }

    private fun searchDrug(drug: Drug, run: Int) {
        pending++
        api("searchMedicine", "name", drug.name) { json ->
            if (run != generation || !drugs.contains(drug)) return@api
            val arr = json?.optJSONArray("medicines")
            val results = (0 until (arr?.length() ?: 0))
                .mapNotNull { arr?.optJSONObject(it) }
            val exact = results.filter {
                normalize(it.optString("name")) == normalize(drug.name) &&
                        it.optString("itemSeq").isNotBlank()
            }
            drug.candidates = emptyList()
            drug.candidateConfirmed = false
            // 기존의 정확 일치 자동 연결을 유지합니다.
            if (exact.size == 1) {
                val item = exact[0]
                drug.name = item.optString("name")
                drug.itemSeq = item.optString("itemSeq")
                drug.matched = true
                fetchDetail(drug, run)
            } else {
                drug.matched = false
                drug.loaded = true
                val normalized = normalize(drug.name)
                drug.candidates = results.mapNotNull { item ->
                    val name = item.optString("name").trim()
                    val seq = item.optString("itemSeq").trim()
                    if (name.isBlank() || seq.isBlank()) null else MedicineCandidate(name, seq)
                }.distinctBy { it.itemSeq }
                    .filter { candidate ->
                        val n = normalize(candidate.name)
                        val distance = editDistance(normalized, n)
                        val limit = if (minOf(normalized.length, n.length) <= 5) 1 else 2
                        distance <= limit || (normalized.length >= 3 &&
                                n.length >= 3 && normalized.take(3) == n.take(3))
                    }.sortedWith(compareBy<MedicineCandidate> {
                        editDistance(normalized, normalize(it.name))
                    }.thenBy { it.name }).take(3)
            }
            pending--
            render()
        }
    }

    private fun fetchDetail(drug: Drug, run: Int) {
        pending++
        api("getMedicineDetail", "itemSeq", drug.itemSeq) { json ->
            if (run != generation || !drugs.contains(drug)) return@api
            if (json?.optBoolean("success") == true && json.optString("itemSeq") == drug.itemSeq) {
                drug.efficacy = json.optString("efficacy")
                drug.sideEffects = json.optString("sideEffects")
                drug.precautions = json.optString("precautions")
                drug.warnings = json.optString("warnings")
                drug.usage = json.optString("usage")
                drug.loaded = true
            } else {
                drug.loaded = false
            }
            pending--
            render()
        }
    }

    // 약 카드에는 핵심 내용만 보여주고, 원문과 후보 목록은 팝업으로 분리합니다.
    private fun render() {
        val holder = findViewById<LinearLayout>(R.id.layoutMedicineSearchCards)
        holder.removeAllViews()
        holder.visibility = View.VISIBLE

        val status = findViewById<TextView>(R.id.txtMedicineSearchResult)
        status.visibility = View.VISIBLE
        status.text = if (pending > 0) "의약품 정보를 확인하고 있어요…" else
            "${drugs.size}종 분석 · ${drugs.count { it.matched }}종 확인 · ${drugs.count { !it.matched }}종 확인 필요"

        findViewById<Button>(R.id.btnMedicineSave).apply {
            visibility = if (drugs.isEmpty()) View.GONE else View.VISIBLE
            isEnabled = pending == 0
        }

        drugs.toList().forEach { drug ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp, 16.dp, 18.dp, 14.dp)
                setBackgroundResource(R.drawable.bg_medicine_card)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = 10.dp
                }
            }

            fun textLine(value: String, size: Float = 14f, bold: Boolean = false,
                         color: String = "#25334B"): TextView =
                TextView(this).apply {
                    text = value
                    textSize = size
                    setTextColor(Color.parseColor(color))
                    if (bold) setTypeface(null, Typeface.BOLD)
                    setPadding(0, 3.dp, 0, 3.dp)
                }

            fun smallButton(label: String, onTap: () -> Unit): Button =
                Button(this).apply {
                    text = label
                    isAllCaps = false
                    textSize = 12f
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(4.dp, 0, 4.dp, 0)
                    setTextColor(Color.parseColor("#285B86"))
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCEFFF"))
                    setOnClickListener { onTap() }
                }

            val title = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            title.addView(
                textLine("💊 ${drug.name}", 17f, true),
                LinearLayout.LayoutParams(0, -2, 1f)
            )
            val verified = drug.matched
            title.addView(
                textLine(
                    if (verified) "✓ 확인" else "⚠ 확인 필요",
                    12f, true,
                    if (verified) "#167A61" else "#B16A18"
                )
            )
            card.addView(title)

            val sourceLabel = when {
                drug.candidateConfirmed -> "사용자가 후보를 확인한 식약처 품목"
                drug.matched -> "식약처 이름 자동 일치"
                else -> "AI가 인식한 약 이름 · 아직 미확인"
            }
            card.addView(textLine(sourceLabel, 12f, false, "#66778D"))

            if (!drug.matched && drug.candidates.isNotEmpty()) {
                card.addView(
                    smallButton("🔎 비슷한 약 ${drug.candidates.size}개 보기") {
                        showMedicineCandidates(drug)
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(-1, 46.dp).apply {
                            topMargin = 8.dp
                            bottomMargin = 8.dp
                        }
                    }
                )
            }

            val isOfficial = drug.matched && drug.loaded
            val hasAiInfo = listOf(
                drug.aiEfficacy, drug.aiSideEffects, drug.aiPrecautions
            ).any { it.isNotBlank() }

            val efficacy = if (isOfficial) drug.efficacy else drug.aiEfficacy
            val sideEffects = if (isOfficial) drug.sideEffects else drug.aiSideEffects
            val precautions = if (isOfficial) drug.precautions else drug.aiPrecautions

            if (isOfficial || (!drug.matched && hasAiInfo)) {
                if (!isOfficial) {
                    card.addView(textLine("✨ AI 참고 설명 · 약 이름 미확인", 12f, true, "#7D65A2"))
                }
                // 한 항목을 한 줄 정도로 요약해 긴 약품 카드가 되지 않도록 합니다.
                card.addView(textLine("효능  ${shortInfo(efficacy, 55)}", 13f))
                card.addView(textLine("부작용  ${shortInfo(sideEffects, 55)}", 13f))
                card.addView(textLine("주의  ${shortInfo(precautions, 55)}", 13f))
                if (!drug.matched) {
                    card.addView(
                        textLine("※ 실제 약과 다를 수 있어요. 약봉투의 제품명·함량을 확인하세요.",
                            11f, false, "#A36A25")
                    )
                }
            } else {
                card.addView(
                    textLine(
                        if (drug.matched) "공식 상세정보를 가져오지 못했어요."
                        else "약 설명을 확인하지 못했어요. 약 이름을 확인해 주세요.",
                        13f, false, "#66778D"
                    )
                )
            }

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8.dp, 0, 0)
            }
            actions.addView(
                smallButton("상세 보기") { showMedicineDetails(drug) },
                LinearLayout.LayoutParams(0, 44.dp, 1f).apply { marginEnd = 6.dp }
            )
            actions.addView(
                smallButton("이름 수정") { editDrug(drug) },
                LinearLayout.LayoutParams(0, 44.dp, 1f).apply { marginEnd = 6.dp }
            )
            actions.addView(
                smallButton("알림 시간") { editTimes(drug) },
                LinearLayout.LayoutParams(0, 44.dp, 1f)
            )
            card.addView(actions)
            holder.addView(card)
        }
    }

    private fun showMedicineCandidates(drug: Drug) {
        if (drug.candidates.isEmpty()) return
        val options = drug.candidates.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("비슷한 약 이름 ${options.size}개")
            .setMessage("약봉투의 제품명과 함량을 직접 대조해 주세요. 이름이 비슷해도 다른 약일 수 있어요.")
            .setItems(options) { _, which ->
                confirmCandidate(drug, drug.candidates[which])
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showMedicineDetails(drug: Drug) {
        val official = drug.matched && drug.loaded
        val detail = if (official) {
            "출처: 식품의약품안전처\n\n" +
                    "효능·효과\n${drug.efficacy.ifBlank { "정보 없음" }}\n\n" +
                    "부작용\n${drug.sideEffects.ifBlank { "정보 없음" }}\n\n" +
                    "주의사항\n${drug.precautions.ifBlank { "정보 없음" }}\n\n" +
                    "경고\n${drug.warnings.ifBlank { "정보 없음" }}\n\n" +
                    "용법·용량\n${drug.usage.ifBlank { "정보 없음" }}"
        } else {
            "AI 참고 설명 · 식약처 공식 확인 정보가 아닙니다.\n" +
                    "약 이름이 확인되지 않았거나 공식 상세정보가 없습니다.\n\n" +
                    "효능 참고\n${drug.aiEfficacy.ifBlank { "정보 없음" }}\n\n" +
                    "부작용 참고\n${drug.aiSideEffects.ifBlank { "정보 없음" }}\n\n" +
                    "주의사항 참고\n${drug.aiPrecautions.ifBlank { "정보 없음" }}\n\n" +
                    "※ 실제 약과 다를 수 있으니 제품명·함량을 확인하세요."
        }
        AlertDialog.Builder(this)
            .setTitle(drug.name)
            .setMessage(detail)
            .setNegativeButton("닫기", null)
            .setNeutralButton("약 이름 수정") { _, _ -> editDrug(drug) }
            .show()
    }

    private fun confirmCandidate(drug: Drug, candidate: MedicineCandidate) {
        AlertDialog.Builder(this)
            .setTitle("이 약이 맞나요?")
            .setMessage("인식된 이름: ${drug.ocrName}\n후보 의약품: ${candidate.name}\n\n약봉투에 적힌 제품명과 함량을 확인해 주세요. 이름이 비슷해도 다른 약일 수 있어요. 확인 전에는 공식 일치로 취급하지 않습니다.")
            .setNegativeButton("아니요", null)
            .setPositiveButton("맞아요 · 연결") { _, _ ->
                drug.name = candidate.name
                drug.itemSeq = candidate.itemSeq
                drug.matched = true
                drug.candidateConfirmed = true
                drug.candidates = emptyList()
                drug.efficacy = ""
                drug.sideEffects = ""
                drug.precautions = ""
                drug.warnings = ""
                drug.usage = ""
                drug.loaded = false
                render()
                fetchDetail(drug, generation)
            }.show()
    }

    private fun editDrug(drug: Drug?) {
        val input = EditText(this).apply {
            setText(drug?.name.orEmpty())
            hint = "약봉투에 적힌 정확한 약 이름"
            setSingleLine()
        }
        AlertDialog.Builder(this).setTitle(if (drug == null) "약 추가" else "약 이름 수정")
            .setView(input).setNegativeButton("취소", null)
            .setPositiveButton("확인") { _, _ ->
                val name = input.text.toString().trim()
                if (name.length < 2) {
                    toast("약 이름을 두 글자 이상 입력해 주세요."); return@setPositiveButton
                }
                val target = drug ?: Drug(name, name).also { drugs.add(it) }
                target.name = name
                target.matched = false
                target.candidateConfirmed = false
                target.candidates = emptyList()
                target.itemSeq = ""
                target.efficacy = ""
                target.sideEffects = ""
                target.precautions = ""
                target.loaded = false
                render()
                searchDrug(target, generation)
            }.show()
    }

    // 기본 알림 시간은 생활 패턴 예시이며 처방 지시가 아닙니다.
    private fun editCommonTimes() {
        val defaults = listOf("08:30", "13:30", "19:30")
        val current = drugs.firstOrNull()?.times.orEmpty()
        val initial = current.ifEmpty { suggestedTimes.ifEmpty { defaults } }
        val labels = listOf("☀️  아침", "🍱  점심", "🌙  저녁")
        val times = defaults.toMutableList()
        initial.take(3).forEachIndexed { index, value -> times[index] = value }
        val enabled = MutableList(3) { index -> index < initial.size }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 12.dp, 20.dp, 4.dp)
        }
        val info = TextView(this).apply {
            text = "추천 알림 시간이에요. 각 시간을 눌러 수정하거나 사용하지 않는 시간은 꺼 주세요.\n약봉투의 실제 복용 횟수와 복용 지시를 먼저 확인해 주세요."
            textSize = 13f
            setTextColor(Color.parseColor("#52647B"))
            setPadding(0, 0, 0, 12.dp)
        }
        panel.addView(info)
        labels.forEachIndexed { index, label ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12.dp, 10.dp, 12.dp, 10.dp)
                setBackgroundColor(Color.parseColor("#F1F7FB"))
            }
            val toggle = CheckBox(this).apply {
                text = label
                textSize = 15f
                isChecked = enabled[index]
                setOnCheckedChangeListener { _, checked -> enabled[index] = checked }
            }
            row.addView(toggle, LinearLayout.LayoutParams(0, -2, 1f))
            val timeButton = Button(this).apply {
                text = times[index]
                isAllCaps = false
                textSize = 15f
                setOnClickListener {
                    val pieces = times[index].split(":")
                    TimePickerDialog(this@MedicineActivity, { _, hour, minute ->
                        times[index] = String.format(Locale.KOREA, "%02d:%02d", hour, minute)
                        text = times[index]
                        toggle.isChecked = true
                    }, pieces[0].toInt(), pieces[1].toInt(), true).show()
                }
            }
            row.addView(timeButton)
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp })
        }
        AlertDialog.Builder(this)
            .setTitle("⏰ 전체 알림 시간 설정")
            .setView(panel)
            .setNegativeButton("취소", null)
            .setNeutralButton("알림 없음") { _, _ ->
                drugs.forEach { it.times = emptyList() }
                render()
            }
            .setPositiveButton("이 시간으로 설정") { _, _ ->
                val selected = times.filterIndexed { index, _ -> enabled[index] }.distinct().sorted()
                drugs.forEach { it.times = selected }
                render()
                toast("${drugs.size}종에 알림 시간을 적용했어요. 약별 복용 지시를 확인해 주세요.")
            }.show()
    }

    private fun editTimes(drug: Drug) {
        val input = EditText(this).apply {
            hint = "예: 08:30, 13:30, 19:30"
            setText(drug.times.joinToString(", ").ifBlank { suggestedTimes.joinToString(", ") })
            setSingleLine()
        }
        AlertDialog.Builder(this).setTitle("${drug.name} 알림 시간")
            .setMessage("복용법이 아니라 알림 시간 설정이에요. 약봉투·약사 지시에 맞게 확인해 주세요. 빈칸이면 알림이 없어요.")
            .setView(input).setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val parsed = parseTimes(input.text.toString())
                if (parsed == null) toast("08:30, 13:30 형식으로 입력해 주세요.")
                else {
                    drug.times = parsed; render()
                }
            }.show()
    }

    private fun parseTimes(s: String): List<String>? {
        if (s.isBlank()) return emptyList()
        val values = s.split(',').map { it.trim() }.distinct().sorted()
        return if (values.size <= 8 && values.all {
                Regex("""(?:[01]\d|2[0-3]):[0-5]\d""").matches(
                    it
                )
            }) values else null
    }

    private fun confirmSave() {
        if (pending > 0 || drugs.isEmpty()) {
            toast("분석이 끝난 후 저장해 주세요."); return
        }
        val user = FirebaseAuth.getInstance().currentUser ?: run { toast("로그인이 필요해요."); return }
        val startInput = EditText(this).apply {
            hint = "복용 기간(일)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(if (prescriptionDays > 0) prescriptionDays.toString() else "")
        }
        AlertDialog.Builder(this).setTitle("복용 일수 확인")
            .setMessage("약봉투에 적힌 복용 일수를 확인해 주세요.")
            .setView(startInput).setNegativeButton("취소", null)
            .setPositiveButton("다음") { _, _ ->
                val days = startInput.text.toString().toIntOrNull()
                if (days == null || days !in 1..365) {
                    toast("복용 일수는 1~365일로 입력해 주세요."); return@setPositiveButton
                }
                val cal = Calendar.getInstance()
                val date = try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply { isLenient = false }
                        .parse(prescriptionDate)
                } catch (_: Exception) {
                    null
                }
                if (date != null) cal.time = date
                DatePickerDialog(
                    this,
                    { _, y, m, d ->
                        val start = String.format(Locale.KOREA, "%04d-%02d-%02d", y, m + 1, d)
                        val unmatched = drugs.filter { !it.matched }
                        val message = buildString {
                            append("약 ${drugs.size}종 · $days 일\n시작일: $start\n\n")
                            append(drugs.joinToString("\n") {
                                "• ${it.name}: ${
                                    if (it.times.isEmpty()) "알림 없음" else it.times.joinToString(
                                        ", "
                                    )
                                }"
                            })
                            if (unmatched.isNotEmpty()) append("\n\n⚠ ${unmatched.size}종은 식약처 품목이 확인되지 않았어요. 공식 약 정보가 확인된 것으로 취급하지 않습니다.")
                            append("\n\n추천 시간은 복용 지시가 아닙니다. 약봉투의 복용 횟수와 식전·식후 지시를 반드시 확인해 주세요.")
                        }
                        AlertDialog.Builder(this).setTitle("최종 확인 후 저장")
                            .setMessage(message).setNegativeButton("수정하기", null)
                            .setPositiveButton("저장") { _, _ -> save(user.uid, start, days) }.show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }.show()
    }

    private fun save(uid: String, start: String, days: Int) {
        val button = findViewById<Button>(R.id.btnMedicineSave)
        button.isEnabled = false
        val medicines = drugs.map { drug ->
            hashMapOf<String, Any>(
                "ocrName" to drug.ocrName,
                "medicineName" to drug.name,
                "itemSeq" to drug.itemSeq,
                "verificationStatus" to when {
                    drug.candidateConfirmed -> "사용자 후보 확인 · 식약처 품목 연결"
                    drug.matched -> "식약처 이름 일치"
                    else -> "확인 필요"
                },
                "status" to "확인 필요",
                "reminderTimes" to drug.times,
                "efficacy" to drug.efficacy,
                "sideEffects" to drug.sideEffects,
                "precautions" to drug.precautions
            )
        }
        val commonTimes = drugs.flatMap { it.times }.distinct().sorted()
        val record = hashMapOf<String, Any>(
            "medicines" to medicines,
            "prescriptionDate" to prescriptionDate,
            "prescriptionDays" to days.toString(),
            "scheduleStartDate" to start,
            "reminderTimes" to commonTimes,
            "status" to "확인 필요",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).collection("medicineRecords").add(record)
            .addOnSuccessListener { doc ->
                // 기존 스케줄러는 처방 단위로 동작하므로 약별 알림 연결 전에는 예약하지 않음.
                // 약별 알림은 MedicineAlarmScheduler 개선 후 활성화해야 함.
                button.isEnabled = true
                toast("처방 기록을 저장했어요. 약별 알림은 아직 연결 작업이 필요해요.")
                startActivity(Intent(this, MedicineRecordActivity::class.java))
            }.addOnFailureListener { e ->
                button.isEnabled = true
                toast("저장 실패: ${e.message}")
            }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}


