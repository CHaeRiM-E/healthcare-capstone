package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class HealthCardNewsActivity : AppCompatActivity() {

    private data class Page(
        val heading: String,
        val content: String
    )

    private data class Topic(
        val emoji: String,
        val title: String,
        val pages: List<Page>
    )

    private val topics = listOf(
        Topic(
            "🥗", "식중독 예방", listOf(
                Page("식중독이란?", "오염된 음식이나 물을 섭취한 뒤 설사, 복통, 구토 등이 발생할 수 있어요."),
                Page("이런 증상을 살펴요", "복통·설사·메스꺼움·구토·발열이 나타날 수 있어요. 심한 탈수에 주의하세요."),
                Page("예방하는 방법", "손을 비누로 씻고, 음식은 충분히 익혀 먹고, 조리 도구를 구분해 사용하세요. 상하기 쉬운 음식은 냉장 보관하세요."),
                Page("아플 때는 이렇게", "물을 조금씩 자주 마시고 휴식하세요. 혈변, 심한 복통, 고열, 탈수 또는 증상 악화 시 진료를 받으세요.")
            )
        ),
        Topic(
            "☀️", "온열질환 예방", listOf(
                Page("더위가 위험한 이유", "고온 환경에서는 체온 조절이 어려워져 열탈진이나 열사병이 생길 수 있어요."),
                Page("주의할 신호", "어지러움, 두통, 심한 땀, 무기력감, 메스꺼움이 나타날 수 있어요. 의식 변화는 특히 위험해요."),
                Page("예방하는 방법", "물을 자주 마시고, 가장 더운 시간대의 야외활동을 줄이고, 그늘이나 시원한 장소에서 쉬세요."),
                Page(
                    "응급 대처",
                    "시원한 곳으로 옮기고 몸을 식혀 주세요. 의식이 흐리거나 쓰러지면 즉시 119에 신고하세요. 의식이 없으면 물을 먹이지 마세요."
                )
            )
        ),
        Topic(
            "😷", "감기·독감 예방", listOf(
                Page("감기와 독감", "둘 다 호흡기 감염이지만 원인 바이러스와 증상의 정도가 달라요. 독감은 갑작스러운 고열·근육통이 동반될 수 있어요."),
                Page("전파를 줄이려면", "손을 자주 씻고, 기침할 때 옷소매로 입과 코를 가리고, 아프면 다른 사람과의 접촉을 줄이세요."),
                Page("생활 속 예방", "실내 환기, 충분한 휴식, 권고 대상자의 독감 예방접종이 도움이 돼요."),
                Page("진료가 필요한 경우", "숨이 차거나 가슴 통증, 의식 변화, 심한 탈수 등이 있으면 신속하게 진료받으세요.")
            )
        ),
        Topic(
            "🩹", "상처 응급처치", listOf(
                Page("상처가 생겼다면", "먼저 손을 씻고 출혈 여부와 상처의 깊이를 살펴보세요."),
                Page("가벼운 상처 관리", "깨끗한 흐르는 물로 상처를 씻고 깨끗한 드레싱으로 보호하세요. 오염된 물질을 무리하게 파내지 마세요."),
                Page("출혈이 있다면", "깨끗한 거즈나 천으로 지속적으로 직접 압박하세요. 출혈이 심하거나 멈추지 않으면 즉시 도움을 요청하세요."),
                Page(
                    "병원에 가야 할 때",
                    "깊거나 벌어진 상처, 감각 이상, 심한 출혈, 감염 징후가 있거나 동물에게 물렸다면 진료받으세요. 파상풍 예방접종 확인도 필요할 수 있어요."
                )
            )
        )
    )

    private lateinit var root: LinearLayout
    private var selectedTopic: Int? = null
    private var pageIndex = 0

    private val navy = Color.rgb(38, 54, 92)
    private val backgroundColor = Color.rgb(243, 246, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(backgroundColor)
        }

        setContentView(
            ScrollView(this).apply {
                addView(root)
            }
        )

        showTopics()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(18).toFloat()
        }

    private fun label(
        text: String,
        size: Float,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(navy)

        if (bold) {
            setTypeface(null, Typeface.BOLD)
        }

        setPadding(dp(8), dp(10), dp(8), dp(10))
    }

    private fun action(
        text: String,
        click: () -> Unit
    ): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(navy)
        background = rounded(Color.WHITE)
        setOnClickListener { click() }

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(62)
        ).apply {
            bottomMargin = dp(12)
        }
    }

    // 현재 월에 따라 기존 카드뉴스 중 하나를 추천
    private fun recommendedTopicIndex(month: Int): Int =
        when (month) {
            5, 6, 9 -> 0  // 식중독
            7, 8 -> 1     // 온열질환
            else -> 2     // 감기·독감
        }

    private fun recommendedDescription(month: Int): String =
        when (month) {
            3, 4 -> "봄철 호흡기 건강관리"
            5, 6 -> "기온 상승기 식중독 예방"
            7, 8 -> "여름철 폭염 건강관리"
            9 -> "가을철 식품 위생관리"
            10, 11 -> "환절기 감기·독감 예방"
            else -> "겨울철 호흡기 건강관리"
        }

    private fun openTopic(index: Int) {
        selectedTopic = index
        pageIndex = 0
        showPage()
    }

    private fun showTopics() {
        selectedTopic = null
        root.removeAllViews()

        root.addView(
            label("← 뒤로", 15f).apply {
                setOnClickListener { finish() }
            }
        )

        root.addView(label("📖 건강정보", 25f, true))
        root.addView(
            label("계절에 맞는 건강정보와 예방수칙을 확인해 보세요.", 14f)
        )

        // 이번 달 추천 건강정보
        val month = Calendar.getInstance()
            .get(Calendar.MONTH) + 1

        val recommendationIndex = recommendedTopicIndex(month)
        val recommended = topics[recommendationIndex]

        root.addView(
            label("🌿 이번 달 추천 건강정보", 19f, true)
        )

        val recommendationCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.WHITE)
            isClickable = true
            elevation = dp(2).toFloat()

            setOnClickListener {
                openTopic(recommendationIndex)
            }
        }

        recommendationCard.addView(
            label("${month}월 추천", 13f)
        )

        recommendationCard.addView(
            label(
                "${recommended.emoji} ${recommended.title}",
                21f,
                true
            )
        )

        recommendationCard.addView(
            label(recommendedDescription(month), 15f)
        )

        recommendationCard.addView(
            label("카드뉴스 자세히 보기 →", 14f)
        )

        root.addView(
            recommendationCard,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(24)
            }
        )

        // 전체 주제 목록
        root.addView(label("📚 주제별 건강정보", 19f, true))

        topics.forEachIndexed { index, topic ->
            root.addView(
                action("${topic.emoji}  ${topic.title}   ›") {
                    openTopic(index)
                }
            )
        }

        // 기존 RSS 건강소식 화면 연결
        root.addView(
            label("📰 최신 건강소식", 19f, true)
        )

        root.addView(
            label(
                "보건복지부와 식품안전나라의 최신 건강 관련 소식을 확인하세요.",
                14f
            )
        )

        root.addView(
            action("최신 건강소식 보기  →") {
                startActivity(
                    Intent(
                        this,
                        HealthNewsActivity::class.java
                    )
                )
            }
        )

        root.addView(
            label(
                "※ 일반적인 건강교육 정보이며 진단이나 진료를 대신하지 않아요.",
                12f
            )
        )
    }

    private fun showPage() {
        val topicIndex = selectedTopic ?: return
        val topic = topics[topicIndex]
        val page = topic.pages[pageIndex]

        root.removeAllViews()

        root.addView(
            label("← 건강정보 목록", 15f).apply {
                setOnClickListener { showTopics() }
            }
        )

        root.addView(
            label("${topic.emoji} ${topic.title}", 24f, true)
        )

        root.addView(
            label(
                "${pageIndex + 1} / ${topic.pages.size}",
                14f
            ).apply {
                gravity = Gravity.END
            }
        )

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            background = rounded(Color.WHITE)
            minimumHeight = dp(280)
        }

        card.addView(label(page.heading, 22f, true))

        card.addView(
            label(page.content, 17f).apply {
                setLineSpacing(dp(6).toFloat(), 1f)
            }
        )

        root.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(20)
            }
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val prev = action("← 이전") {
            if (pageIndex > 0) {
                pageIndex--
                showPage()
            }
        }

        prev.isEnabled = pageIndex > 0

        controls.addView(
            prev,
            LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                marginEnd = dp(6)
            }
        )

        val next = action(
            if (pageIndex == topic.pages.lastIndex)
                "목록으로"
            else
                "다음 →"
        ) {
            if (pageIndex == topic.pages.lastIndex) {
                showTopics()
            } else {
                pageIndex++
                showPage()
            }
        }

        controls.addView(
            next,
            LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                marginStart = dp(6)
            }
        )

        root.addView(controls)

        root.addView(
            label("응급 상황에는 119에 연락하세요.", 12f)
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (selectedTopic != null) {
            showTopics()
        } else {
            super.onBackPressed()
        }
    }
}
