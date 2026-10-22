package com.example.myapplication

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

/** 계절별 건강교육 정보: 외부 API 없이 기기 날짜를 기준으로 표시합니다. */
class SeasonalHealthActivity : AppCompatActivity() {
    private data class Topic(val title: String, val summary: String, val details: String)
    private data class Season(
        val label: String,
        val emoji: String,
        val intro: String,
        val topics: List<Topic>
    )

    private val seasons = listOf(
        Season(
            "봄", "🌸", "꽃가루와 미세먼지, 일교차에 대비해요.", listOf(
                Topic(
                    "꽃가루 알레르기",
                    "외출 후 세안·옷 갈아입기",
                    "꽃가루가 많은 날에는 창문을 오래 열어두지 않고, 외출 후 손과 얼굴을 씻고 겉옷을 정리하세요. 증상이 반복되면 의료진과 상담하세요."
                ),
                Topic(
                    "미세먼지 대처",
                    "대기질 확인 후 외출 계획",
                    "에어코리아 등 공식 대기질 정보를 확인하세요. 대기질이 나쁜 날에는 야외 활동을 줄이고, 필요할 때 적절한 마스크를 착용하세요."
                ),
                Topic("일교차 관리", "겹쳐 입을 옷과 충분한 휴식", "아침저녁 기온 차에 대비해 얇은 옷을 겹쳐 입고, 충분히 쉬며 수분을 섭취하세요.")
            )
        ),
        Season(
            "여름", "☀️", "더위와 습도로 인한 건강 문제를 예방해요.", listOf(
                Topic(
                    "온열질환 예방",
                    "그늘·수분·휴식",
                    "더운 시간대에는 무리한 야외 활동을 피하고, 시원한 장소에서 자주 쉬며 물을 마시세요. 의식 저하나 혼란이 생기면 즉시 119에 신고하세요."
                ),
                Topic(
                    "식중독 예방",
                    "손 씻기와 음식 보관",
                    "조리 전후 손을 씻고, 음식은 충분히 익혀 먹으세요. 상하기 쉬운 음식은 냉장 보관하고 실온에 오래 두지 마세요."
                ),
                Topic(
                    "여름철 수분 관리",
                    "갈증 전에 조금씩 마시기",
                    "활동량과 날씨에 맞춰 물을 충분히 마시세요. 심장·신장 질환 등으로 수분 제한을 안내받았다면 의료진 지침을 우선하세요."
                )
            )
        ),
        Season(
            "가을", "🍂", "큰 일교차와 건조한 공기에 대비해요.", listOf(
                Topic(
                    "호흡기 감염 예방",
                    "손 위생과 환기",
                    "비누로 손을 자주 씻고, 사람이 많은 실내에서는 환기와 기침 예절을 지키세요. 증상이 심하거나 오래가면 진료를 받으세요."
                ),
                Topic("피부 보습", "짧은 샤워와 보습제", "너무 뜨거운 물로 오래 씻지 않고, 씻은 직후 보습제를 발라 건조함을 줄이세요."),
                Topic("일교차 대비", "체온 유지와 충분한 수면", "외출 시 겉옷을 챙기고 수면과 식사를 규칙적으로 유지하세요.")
            )
        ),
        Season(
            "겨울", "❄️", "추위와 실내 건조에 대비해요.", listOf(
                Topic(
                    "한랭질환 예방",
                    "보온과 장시간 노출 피하기",
                    "외출할 때 모자·장갑 등으로 보온하고, 추위에 장시간 노출되지 않도록 하세요. 심한 떨림, 혼란, 의식 저하가 있으면 119에 신고하세요."
                ),
                Topic(
                    "독감 등 호흡기 감염",
                    "예방접종 정보 확인",
                    "예방접종 대상과 일정은 질병관리청 등 공식 안내를 확인하세요. 손 씻기, 환기, 기침 예절도 중요합니다."
                ),
                Topic(
                    "실내 건조 관리",
                    "적절한 환기와 보습",
                    "난방 중에도 주기적으로 환기하고, 피부가 건조하면 보습제를 사용하세요. 가습기는 깨끗하게 관리하세요."
                )
            )
        )
    )

    private lateinit var body: LinearLayout
    private var selected = 0
    private val ink = Color.parseColor("#26365C")
    private val blue = Color.parseColor("#5F86F2")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = when (Calendar.getInstance().get(Calendar.MONTH) + 1) {
            in 3..5 -> 0
            in 6..8 -> 1
            in 9..11 -> 2
            else -> 3
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F3F6FF"))
        }
        val header = TextView(this).apply {
            text = "‹    계절별 건강정보"
            textSize = 21f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(22), dp(20), dp(22))
            setBackgroundColor(Color.WHITE)
            setOnClickListener { finish() }
        }
        root.addView(header)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun render() {
        body.removeAllViews()
        val season = seasons[selected]
        body.addView(text("${season.emoji}  ${season.label}철 건강관리", 23f, true, ink).apply {
            setPadding(dp(4), dp(8), dp(4), dp(8))
        })
        body.addView(text(season.intro, 14f, false, Color.parseColor("#66738B")).apply {
            setPadding(dp(4), 0, dp(4), dp(18))
        })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        seasons.forEachIndexed { index, item ->
            tabs.addView(
                text(
                    item.label,
                    14f,
                    selected == index,
                    if (selected == index) Color.WHITE else ink
                ).apply {
                    gravity = Gravity.CENTER
                    background = rounded(if (selected == index) blue else Color.WHITE, 15f)
                    setPadding(dp(4), dp(12), dp(4), dp(12))
                    setOnClickListener { selected = index; render() }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        body.addView(tabs)
        body.addView(text("건강관리 주제", 18f, true, ink).apply {
            setPadding(dp(4), dp(26), 0, dp(12))
        })
        season.topics.forEach { topic ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Color.WHITE, 17f)
                setPadding(dp(18), dp(17), dp(18), dp(17))
                isClickable = true
                setOnClickListener {
                    AlertDialog.Builder(this@SeasonalHealthActivity)
                        .setTitle(topic.title)
                        .setMessage(topic.details)
                        .setPositiveButton("확인", null)
                        .show()
                }
            }
            card.addView(text("${topic.title}   ›", 17f, true, ink))
            card.addView(text(topic.summary, 13f, false, Color.parseColor("#66738B")).apply {
                setPadding(0, dp(7), 0, 0)
            })
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        }
        body.addView(
            text(
                "※ 일반적인 건강교육 정보이며 진단을 대신하지 않습니다. 응급 증상이 있으면 119에 연락하세요.",
                12f,
                false,
                Color.parseColor("#778198")
            ).apply {
                setPadding(dp(5), dp(15), dp(5), dp(5))
            })
    }

    private fun text(value: String, size: Float, bold: Boolean, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
