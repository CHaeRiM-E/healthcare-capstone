package com.example.myapplication

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import androidx.activity.result.contract.ActivityResultContracts
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
import android.view.WindowManager
import androidx.core.view.updatePadding

class InternalChatActivity : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnSaveChat: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var chatScrollView: ScrollView
    private var quickReplyContainer: LinearLayout? = null
    private var isWaitingForAI = false
    private var emergencyRuleTriggered = false
    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) confirmInjuryPhoto(uri)
        }


    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build()

    // AI에 전달되는 전체 대화
    private val messages = JSONArray()

    // 기록 화면에 저장할 실제 상담 내용
    private val chatHistory = mutableListOf<Map<String, String>>()

    private val systemPrompt = """
        너는 응급 상황 상담을 돕는 AI 건강 문진 챗봇이다. 내상과 외상 모두 상담한다.
        친절한 존댓말로 한 번에 질문 하나만 한다. 위치, 양상, 시작 시점, 동반 증상 등 필요한 정보를 단계별로 확인한다.
        확정 진단을 하지 않는다. 응급 증상이 의심되면 문진 완료를 기다리지 말고 즉시 119 또는 응급실 안내를 한다.
        위험 신호가 확인되면 질문을 계속하지 말고 즉시 긴급 안내를 한다. 위험도가 충분히 평가되면 반드시 '응급도: 낮음', '응급도: 주의', '응급도: 긴급' 중 하나를 명시하고 근거와 다음 행동을 설명한다.
        의료 정보는 참고용이며 의료진 진료를 대신하지 않는다고 안내한다.

        응답은 반드시 아래 JSON 객체 하나로만 반환한다. 코드블록이나 JSON 외 설명을 쓰지 않는다.
        {"reply":"사용자에게 보여줄 짧고 자연스러운 설명과 질문 한 개", "options":["선택지1","선택지2"]}
        options는 방금 질문에 대한 선택지 2~6개를 짧은 한국어로 제공한다.
        위치, 통증 느낌, 기간, 빈도, 동반 증상 등 객관식으로 답하기 좋은 질문마다 매번 새로운 options를 만든다.
        답변을 직접 입력할 수도 있으므로 options에 반드시 '잘 모르겠어요'를 넣을 필요는 없다.
        단순 안내, 응급 상황 경고, 최종 응급도 판단 등 사용자의 선택을 기다리지 않는 경우 options는 빈 배열 []로 한다.
        reply 안에는 선택지를 목록으로 다시 나열하지 않는다. 선택지는 options에만 넣는다.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_internal_chat)
        val rootView = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->

            val keyboardVisible = insets.isVisible(
                WindowInsetsCompat.Type.ime()
            )

            val keyboardHeight = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            ).bottom

            val navigationBarHeight = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            val bottomPadding = if (keyboardVisible) {
                (keyboardHeight - navigationBarHeight).coerceAtLeast(0)
            } else {
                0
            }

            view.updatePadding(bottom = bottomPadding)

            insets
        }

        ViewCompat.requestApplyInsets(rootView)

        chatContainer = findViewById(R.id.chatContainer)

        etMessage = findViewById(R.id.etMessage)

        btnSend = findViewById(R.id.btnSend)

        btnSaveChat = findViewById(R.id.btnSaveChat)

        progressBar = findViewById(R.id.progressBar)

        chatScrollView = findViewById(R.id.chatScrollView)

        val btnBack = findViewById<Button>(R.id.btnBack)

        // 키보드가 보일 때 입력창 공간을 확보하고 상담 종료 버튼은 잠시 숨깁니다.
        val root = findViewById<View>(R.id.chatRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            btnSaveChat.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
            if (keyboardVisible) scrollToBottom()
            insets
        }
        ViewCompat.requestApplyInsets(root)

        // 시스템 프롬프트
        messages.put(
            JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })

        val fromInjuryResult = intent.getBooleanExtra("fromInjuryResult", false)
        val injuryName = intent.getStringExtra("injuryName")?.trim().orEmpty()
        val confidence = intent.getDoubleExtra("confidence", -1.0)
        if (fromInjuryResult) {
            val scoreDescription = if (confidence in 0.0..100.0)
                String.format(java.util.Locale.KOREA, "%.1f%%", confidence)
            else "표시 없음 (사용자 수정 또는 미측정)"
            val context = """
                사용자가 외상 사진 분석 결과 화면에서 추가 상담을 요청했다.
                사진 분석에서 표시된 상처 종류: $injuryName
                AI 탐지 점수: $scoreDescription
                사진 자체는 이 상담 모델에 전송되지 않았으며, 상처 분류는 확정 진단이 아니다.
                위 결과를 참고하되, 첫 답변에서는 상처에 맞는 위험 신호를 확인하는 질문을 하나만 하고 선택지를 제공하라.
                출혈이 압박해도 멈추지 않음, 호흡곤란, 의식저하 등 중대한 응급 신호가 확인되면 즉시 119 안내를 우선하라.
                최종 응급도는 사진 탐지 점수로 판단하지 말고 사용자가 답한 증상과 위험 신호로 판단하라.
            """.trimIndent()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", context)
            })
            val greeting = "💊 외상 분석 결과: $injuryName\n" +
                    "AI 탐지 점수: $scoreDescription\n\n" +
                    "이 결과는 상처 종류를 추정한 것이며 응급도를 뜻하지 않아요. " +
                    "이제 증상을 자세히 확인할게요.\n\n" +
                    "먼저 언제 다쳤고, 지금 가장 불편한 증상이 무엇인지 알려주세요."
            addBotMessage(greeting)
        } else {
            val greeting = "안녕하세요 😊\n\n두통·복통 같은 몸의 증상과 상처·화상 같은 외상 모두 이곳에서 상담할 수 있어요.\n증상을 입력하거나 아래 + 버튼으로 상처 사진 분석을 시작해 주세요."
            addBotMessage(greeting)
        }

        // 하나의 채팅 화면에서 내상/외상을 안내합니다. 상담 유형 선택으로 화면을 분리하지 않습니다.
        if (!fromInjuryResult) addUnifiedConsultationGuide()

        btnBack.setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            submitUserMessage(etMessage.text.toString().trim())
        }

        // 입력 영역에 어울리는 작은 사진 첨부 아이콘 버튼 (기존 XML 변경 없음)
        val photoButton = Button(this).apply {
            text = "＋"
            contentDescription = "상처 사진 첨부 및 AI 외상 분석"
            textSize = 23f
            setTypeface(null, Typeface.NORMAL)
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setTextColor(Color.parseColor("#3A86FF"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EAF4FF"))
            setOnClickListener { photoPicker.launch("image/*") }
        }
        val sendParent = btnSend.parent as? android.view.ViewGroup
        if (sendParent is LinearLayout) {
            val index = sendParent.indexOfChild(btnSend)
            val params = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(6)
            }
            sendParent.addView(photoButton, index.coerceAtLeast(0), params)
        } else {
            // 비표준 레이아웃에서도 사진 첨부 기능은 유지
            photoButton.text = "＋  상처 사진 첨부"
            chatContainer.addView(photoButton, 0)
        }


        btnSaveChat.setOnClickListener {
            confirmSaveChat()
        }
    }

    private fun addUnifiedConsultationGuide() {
        val guide = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground("#F1F8FF", 18, "#D6E9FB")
        }
        fun guideText(value: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.parseColor("#263B53"))
            if (bold) setTypeface(null, Typeface.BOLD)
        }
        guide.addView(guideText("AI 건강 상담 · 내상 + 외상", 16f, true))
        guide.addView(guideText("증상을 글로 설명하거나 상처 사진을 분석할 수 있어요.", 13f).apply {
            setPadding(0, dp(6), 0, dp(10))
        })
        val examples = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun example(label: String, message: String): TextView = TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#285B86"))
            background = roundedBackground("#FFFFFF", 12, "#B8DFFF")
            setPadding(dp(8), dp(11), dp(8), dp(11))
            setOnClickListener { submitUserMessage(message) }
        }
        examples.addView(example("🤒 두통·복통", "몸이 불편해서 상담받고 싶어요."),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        examples.addView(example("🩹 상처·화상", "상처가 생겨서 상담받고 싶어요."),
            LinearLayout.LayoutParams(0, -2, 1f))
        guide.addView(examples)
        guide.addView(guideText("📷 상처 사진: 입력창 옆 + 버튼 → AI 사진 분석", 12f).apply {
            setPadding(0, dp(10), 0, 0)
        })
        chatContainer.addView(guide, 0, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
    }

    private fun confirmInjuryPhoto(uri: Uri) {
        // 키보드가 떠 있어도 취소/분석 버튼이 화면 아래로 밀리지 않도록
        // 미리보기와 확인 버튼을 하나의 고정 높이 다이얼로그에 배치합니다.
        (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow(etMessage.windowToken, 0)
        etMessage.clearFocus()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(14))
        }
        val description = TextView(this).apply {
            text = "선택한 사진을 AI 외상 분석으로 전송할까요?"
            textSize = 14f
            setTextColor(Color.parseColor("#263B53"))
            setPadding(0, 0, 0, dp(8))
        }
        panel.addView(description)
        val preview = ImageView(this).apply {
            setImageURI(uri)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#F5FAFF"))
        }
        panel.addView(preview, LinearLayout.LayoutParams(-1, dp(112)))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        val cancel = Button(this).apply {
            text = "취소"
            isAllCaps = false
            setTextColor(Color.parseColor("#285B86"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E1F1FF"))
        }
        val analyze = Button(this).apply {
            text = "분석하기"
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3A86FF"))
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        buttons.addView(analyze, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(buttons)
        val dialog = AlertDialog.Builder(this)
            .setTitle("상처 사진 분석")
            .setView(panel)
            .create()
        cancel.setOnClickListener { dialog.dismiss() }
        analyze.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AnalysisActivity::class.java).apply {
                putExtra("imageUri", uri.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    private fun submitUserMessage(message: String) {
        if (message.isBlank() || isWaitingForAI) return
        clearQuickReplies()
        addUserMessage(message)
        chatHistory.add(mapOf("role" to "user", "content" to message))
        etMessage.setText("")
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", message)
        })
        // 명확한 응급 신호는 모델 응답을 기다리지 않고 우선 안내합니다.
        if (hasClearEmergencySignal(message)) {
            emergencyRuleTriggered = true
            val warning = "응급도: 긴급\n말씀하신 증상은 응급 상황일 수 있습니다. " +
                    "지금 바로 119에 연락하거나 응급실 도움을 받으세요. " +
                    "혼자 이동하거나 직접 운전하지 마세요. 이 안내는 진단이 아닙니다."
            messages.put(JSONObject().apply {
                put("role", "assistant")
                put("content", warning)
            })
            addBotMessage(warning)
            chatHistory.add(mapOf("role" to "assistant", "content" to warning))
            return
        }
        sendMessageToAI()
    }

    // 부정 표현을 포함한 단순 키워드 매칭은 피하고, 명백한 표현만 우선 탐지합니다.
    // 모든 응급상황을 탐지할 수 없으므로 AI 상담과 별개로 119 안내를 제공합니다.
    private fun hasClearEmergencySignal(input: String): Boolean {
        val compact = input.replace(Regex("\\s+"), "")
        val negated = listOf("아니에요", "아닙니다", "않아요", "예전", "과거", "가정", "만약")
        val patterns = listOf(
            "숨을못쉬", "숨이안쉬", "호흡이안돼", "호흡이안되", "질식", "의식을잃", "의식이없",
            "정신을잃", "피가안멈", "출혈이안멈", "피가계속쏟", "경련을하고", "발작을하고"
        )
        return patterns.any { signal ->
            val pos = compact.indexOf(signal)
            if (pos < 0) false else {
                val near =
                    compact.substring(pos, (pos + signal.length + 8).coerceAtMost(compact.length))
                !negated.any { near.contains(it) }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun clearQuickReplies() {
        quickReplyContainer?.let { (it.parent as? LinearLayout)?.removeView(it) }
        quickReplyContainer = null
    }

    private fun showQuickReplies(options: List<String>) {
        clearQuickReplies()
        if (options.isEmpty()) return
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = roundedBackground("#FFFFFF", 18, "#E0E7FA")
        }
        val title = TextView(this).apply {
            text = "답변을 선택하거나 직접 입력해 주세요"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#66738D"))
            setPadding(dp(3), 0, 0, dp(8))
        }
        holder.addView(title)
        options.distinct().take(8).chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { option ->
                val choice = TextView(this).apply {
                    text = option
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#2468C9"))
                    background = roundedBackground("#EDF5FF", 12, "#D7E8FF")
                    setPadding(dp(8), dp(13), dp(8), dp(13))
                    setOnClickListener { submitUserMessage(option) }
                }
                row.addView(
                    choice,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            holder.addView(row)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(4), dp(26), dp(12)) }
        chatContainer.addView(holder, params)
        quickReplyContainer = holder
        scrollToBottom()
    }

    private fun roundedBackground(
        fill: String, radius: Int, stroke: String
    ): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor(fill))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), android.graphics.Color.parseColor(stroke))
        }

    private data class AiReply(val text: String, val options: List<String>)

    private fun parseAiReply(raw: String): AiReply {
        val trimmed =
            raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val obj = JSONObject(trimmed)
            val reply = obj.optString("reply").ifBlank { raw }
            val arr = obj.optJSONArray("options")
            val opts = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull {
                arr.optString(it).trim().takeIf { item -> item.isNotBlank() }
            }
            AiReply(reply, opts)
        } catch (_: Exception) {
            // API가 일반 텍스트로 응답하더라도 상담은 계속할 수 있게 합니다.
            AiReply(raw, emptyList())
        }
    }

    private fun sendMessageToAI() {

        val apiKey = getString(R.string.factchat_api_key)

        if (apiKey.isBlank()) {
            addBotMessage(
                "API 키가 설정되어 있지 않습니다."
            )
            return
        }

        isWaitingForAI = true
        clearQuickReplies()
        progressBar.visibility = View.VISIBLE

        btnSend.isEnabled = false

        val json = JSONObject().apply {
            put(
                "model", "claude-sonnet-4-6"
            )

            put(
                "messages", messages
            )

            put(
                "stream", false
            )
        }

        val requestBody = json.toString().toRequestBody(
            "application/json; charset=utf-8".toMediaType()
        )

        val request = Request.Builder().url(
            "https://factchat-cloud.mindlogic.ai/" + "v1/gateway/chat/completions/"
        ).addHeader(
            "Authorization", "Bearer $apiKey"
        ).addHeader(
            "Content-Type", "application/json"
        ).post(requestBody).build()

        client.newCall(request).enqueue(

            object : Callback {

                override fun onFailure(
                    call: Call, e: IOException
                ) {

                    runOnUiThread {

                        progressBar.visibility = View.GONE

                        btnSend.isEnabled = true
                        isWaitingForAI = false

                        addBotMessage(
                            "AI 서버에 연결하지 못했습니다.\n" + "인터넷 연결을 확인해주세요."
                        )
                    }
                }

                override fun onResponse(
                    call: Call, response: Response
                ) {

                    response.use {

                        val responseText = response.body?.string().orEmpty()

                        if (!response.isSuccessful) {

                            runOnUiThread {

                                progressBar.visibility = View.GONE

                                btnSend.isEnabled = true
                                isWaitingForAI = false

                                addBotMessage(
                                    "AI 응답 오류가 발생했습니다.\n" + "오류 코드: ${response.code}"
                                )
                            }

                            return
                        }

                        try {

                            val responseJson = JSONObject(
                                responseText
                            )

                            val choices = responseJson.getJSONArray(
                                "choices"
                            )

                            val firstChoice = choices.getJSONObject(0)

                            val messageObject = firstChoice.getJSONObject(
                                "message"
                            )

                            val answer = messageObject.getString(
                                "content"
                            )

                            val parsed = parseAiReply(answer)

                            messages.put(
                                JSONObject().apply {
                                    put(
                                        "role", "assistant"
                                    )

                                    put(
                                        "content", answer
                                    )
                                })

                            runOnUiThread {

                                progressBar.visibility = View.GONE

                                btnSend.isEnabled = true
                                isWaitingForAI = false

                                addBotMessage(parsed.text)
                                showQuickReplies(parsed.options)

                                chatHistory.add(
                                    mapOf(
                                        "role" to "assistant", "content" to parsed.text
                                    )
                                )
                            }

                        } catch (e: Exception) {

                            runOnUiThread {

                                progressBar.visibility = View.GONE

                                btnSend.isEnabled = true
                                isWaitingForAI = false

                                addBotMessage(
                                    "AI의 응답을 읽는 중 " + "오류가 발생했습니다."
                                )
                            }
                        }
                    }
                }
            })
    }

    private fun confirmSaveChat() {

        if (chatHistory.isEmpty()) {

            Toast.makeText(
                this, "저장할 상담 내용이 없습니다.", Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (progressBar.visibility == View.VISIBLE) {

            Toast.makeText(
                this, "AI 답변이 끝난 후 저장해주세요.", Toast.LENGTH_SHORT
            ).show()

            return
        }

        AlertDialog.Builder(this).setTitle("상담 종료 및 저장").setMessage(
            "현재 상담 내용을 기록에 저장할까요?"
        ).setNegativeButton(
            "취소", null
        ).setPositiveButton(
            "저장"
        ) { _, _ ->

            saveChatRecord()
        }.show()
    }

    private fun saveChatRecord() {

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {

            Toast.makeText(
                this, "로그인 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT
            ).show()

            return
        }

        btnSaveChat.isEnabled = false

        btnSaveChat.text = "저장 중..."

        val fromInjuryResult = intent.getBooleanExtra("fromInjuryResult", false)
        val injuryName = intent.getStringExtra("injuryName")?.trim().orEmpty()
        val confidence = intent.getDoubleExtra("confidence", -1.0)

        val firstUserMessage = chatHistory.firstOrNull {
            it["role"] == "user"
        }?.get("content") ?: if (fromInjuryResult) "외상 추가 상담" else "건강 상담"

        val risk = findRiskLevel()

        val record = hashMapOf<String, Any>(
            "title" to makeRecordTitle(
                firstUserMessage
            ),

            "firstMessage" to firstUserMessage,

            "risk" to risk, "consultationStatus" to "상담 완료", "endReason" to "사용자 직접 종료",

            "messages" to ArrayList(chatHistory),

            "createdAt" to FieldValue.serverTimestamp()
        )

        // 외상 결과에서 이어진 상담은 기존 외상 기록 컬렉션에 함께 저장합니다.
        // 기존 injuryRecords의 분석 기록은 변경하거나 삭제하지 않습니다.
        val collectionName = if (fromInjuryResult)
            "injuryRecords" else "internalRecords"
        if (collectionName == "injuryRecords") {
            record["recordType"] = "aiConsultation"
            record["injuryName"] = injuryName.ifBlank { "상처 종류 미확인" }
            record["confidence"] = if (confidence in 0.0..100.0) confidence else 0.0
            record["imageUri"] = ""
            record["emergency"] = "AI 상담 문진 결과: $risk"
            record["firstAid"] = "상담 내용을 확인해주세요."
        }

        FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .collection(collectionName).add(record).addOnSuccessListener {

                Toast.makeText(
                    this,
                    if (collectionName == "injuryRecords") "외상 상담 기록이 저장되었습니다." else "상담 기록이 저장되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }.addOnFailureListener {

                btnSaveChat.isEnabled = true

                btnSaveChat.text = "상담 종료 및 저장"

                Toast.makeText(
                    this, "상담 기록을 저장하지 못했습니다.", Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun makeRecordTitle(
        firstMessage: String
    ): String {

        val clean = firstMessage.replace("\n", " ").trim()

        return if (clean.length > 20) {

            clean.take(20) + "..."

        } else {

            clean
        }
    }

    private fun findRiskLevel(): String {
        val pattern = Regex("응급도\\s*[:：·-]\\s*(긴급|주의|낮음)")
        if (emergencyRuleTriggered) return "긴급"
        for (item in chatHistory.asReversed()) {
            if (item["role"] != "assistant") continue
            val match = pattern.find(item["content"].orEmpty())
            if (match != null) return match.groupValues[1]
        }
        return if (emergencyRuleTriggered) "긴급" else "미평가"
    }

    private fun addUserMessage(
        message: String
    ) {

        val textView = TextView(this)

        textView.text = message

        textView.textSize = 16f

        textView.setTextColor(
            getColor(
                android.R.color.white
            )
        )

        textView.setBackgroundResource(
            R.drawable.bg_chat_user
        )

        textView.setPadding(
            30, 20, 30, 20
        )

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,

            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.gravity = Gravity.END

        params.setMargins(
            80, 10, 0, 10
        )

        textView.layoutParams = params

        chatContainer.addView(
            textView
        )

        scrollToBottom()
    }

    // 최종 응급도 평가만 읽기 쉬운 카드로 표시합니다.
    // 원본 AI 답변은 수정하지 않으므로 Firestore 상담 기록은 그대로 유지됩니다.
    private fun showResultCardIfFinal(message: String): Boolean {
        val riskMatch = Regex("응급도\\s*[:：·-]\\s*(낮음|주의|긴급)").find(message) ?: return false
        val risk = riskMatch.groupValues[1]
        val color = when (risk) {
            "긴급" -> "#B42318"
            "주의" -> "#B25E12"
            else -> "#327B61"
        }
        val tint = when (risk) {
            "긴급" -> "#FFF0EE"
            "주의" -> "#FFF5E7"
            else -> "#EAF7F0"
        }

        fun bg(fill: String, stroke: String = fill): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor(fill))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), android.graphics.Color.parseColor(stroke))
            }

        fun text(
            content: String, size: Float, bold: Boolean = false, colorHex: String = "#283247"
        ) = TextView(this).apply {
            this.text = content
            textSize = size
            setTextColor(android.graphics.Color.parseColor(colorHex))
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1.08f)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg("#FFFFFF", "#DFE6F4")
            setPadding(dp(17), dp(18), dp(17), dp(18))
        }
        card.addView(text("상담 결과", 18f, true))
        val badge = text("응급도  $risk", 16f, true, color).apply {
            background = bg(tint)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        card.addView(
            badge, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12); bottomMargin = dp(12) })

        val withoutRisk = message.replace(riskMatch.value, "").trim()
        val chunks =
            withoutRisk.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        chunks.forEachIndexed { index, chunk ->
            val heading = when {
                chunk.contains("다음 행동") || chunk.contains("권장") || chunk.contains("대처") || chunk.contains(
                    "주의사항"
                ) || chunk.contains("•") -> "권장 행동"

                chunk.contains("본 안내는") || chunk.contains("의료진의 진료") || chunk.contains("참고용") -> "안내 사항"
                else -> if (index == 0) "판단 내용" else "추가 안내"
            }
            val section = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = bg("#F7F9FD")
                setPadding(dp(13), dp(13), dp(13), dp(13))
            }
            section.addView(text(heading, 13f, true, "#3A86FF"))
            val cleaned =
                chunk.replace(Regex("^(다음 행동을 권장드립니다|다음 행동|권장 행동|판단 이유)[:：]?\\s*"), "").trim()
                    .ifEmpty { chunk }
            section.addView(
                text(cleaned, 14.5f), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(7) })
            card.addView(
                section, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) })
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(8), dp(8), dp(12)) }
        val findHospital = Button(this).apply {
            text = if (risk == "긴급") "119 도움 요청 · 병원 찾기" else "가까운 병원 찾기  →"
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3A86FF"))
            setOnClickListener {
                if (risk == "긴급") {
                    AlertDialog.Builder(this@InternalChatActivity)
                        .setTitle("응급 상황 안내")
                        .setMessage("위급한 증상이 있다면 병원 검색보다 119 신고가 우선입니다.")
                        .setPositiveButton("119 전화") { _, _ ->
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119")))
                        }
                        .setNeutralButton("병원 찾기") { _, _ ->
                            startActivity(
                                Intent(
                                    this@InternalChatActivity,
                                    HospitalActivity::class.java
                                )
                            )
                        }
                        .setNegativeButton("취소", null)
                        .show()
                } else {
                    startActivity(Intent(this@InternalChatActivity, HospitalActivity::class.java))
                }
            }
        }
        card.addView(findHospital, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(6)
        })
        chatContainer.addView(card, params)
        scrollToBottom()
        return true
    }

    private fun addBotMessage(
        message: String
    ) {
        if (showResultCardIfFinal(message)) return

        val textView = TextView(this)

        val formattedMessage = android.text.TextUtils.htmlEncode(message).replace(
            Regex(
                "\\*\\*(.+?)\\*\\*"
            ), "<b>$1</b>"
        ).replace(
            "\n", "<br>"
        )

        textView.text = HtmlCompat.fromHtml(
            formattedMessage, HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        textView.textSize = 16f

        textView.setTextColor(
            getColor(
                android.R.color.black
            )
        )

        textView.setBackgroundResource(
            R.drawable.bg_chat_bot
        )

        textView.setPadding(
            30, 20, 30, 20
        )

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,

            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.gravity = Gravity.START

        params.setMargins(
            0, 10, 80, 10
        )

        textView.layoutParams = params

        chatContainer.addView(
            textView
        )

        scrollToBottom()
    }

    private fun scrollToBottom() {

        chatScrollView.post {

            chatScrollView.fullScroll(
                View.FOCUS_DOWN
            )
        }
    }
}
