package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HealthNewsActivity : AppCompatActivity() {
    private data class News(
        val title: String,
        val url: String,
        val date: String,
        val source: String,
        val time: Long
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // 실제 RSS URL을 일반 문자열로 작성합니다. 사이트 운영 정책에 따라 주소가 바뀔 수 있습니다.
    private val feeds = listOf(
        "보건복지부" to "https://www.mohw.go.kr/rss/board.es?mid=a10503000000&bid=0027&info=",
        "식품안전나라" to "https://www.foodsafetykorea.go.kr/rss/rss.do?bbs_no=bbs082&menu_no=2859&menu_grp=MENU_NEW05"
    )

    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var refresh: Button
    private var loadId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(Color.rgb(243, 246, 255))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply {
            text = "‹"
            textSize = 24f
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(56), dp(52)))
        header.addView(TextView(this).apply {
            text = "건강소식"
            textSize = 23f
            setTextColor(Color.rgb(32, 44, 67))
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        refresh = Button(this).apply {
            text = "새로고침"
            setOnClickListener { loadNews() }
        }
        header.addView(refresh)
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "공공기관의 최신 건강 관련 소식을 확인해 보세요.\n기사를 누르면 공식 원문으로 이동합니다."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(dp(4), dp(8), dp(4), dp(12))
        })
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(dp(4), dp(2), dp(4), dp(10))
        }
        root.addView(status)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        loadNews()
    }

    private fun loadNews() {
        val requestId = ++loadId
        refresh.isEnabled = false
        status.text = "최신 소식을 불러오는 중…"
        list.removeAllViews()
        Thread {
            val all = mutableListOf<News>()
            val errors = mutableListOf<String>()
            feeds.forEach { (source, url) ->
                try {
                    val request = Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Android; HealthNewsApp/1.0)")
                        .header(
                            "Accept",
                            "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
                        )
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val xml = response.body?.string() ?: error("빈 응답")
                        val parsed = parseRss(xml, source)
                        if (parsed.isEmpty()) error("기사 항목 없음 또는 RSS 형식 변경")
                        all.addAll(parsed)
                    }
                } catch (e: Exception) {
                    errors.add("$source: ${e.message ?: "연결 실패"}")
                }
            }
            val sorted = all.distinctBy { it.url.ifBlank { it.title } }
                .sortedWith(compareByDescending<News> { it.time }.thenBy { it.title }).take(40)
            runOnUiThread {
                if (isFinishing || isDestroyed || requestId != loadId) return@runOnUiThread
                refresh.isEnabled = true
                status.text = when {
                    sorted.isEmpty() -> "기사를 불러오지 못했어요.\n${errors.joinToString("\n")}\n인터넷 연결과 기관 RSS 주소를 확인해 주세요."
                    errors.isNotEmpty() -> "실제 기사 ${sorted.size}건 · 일부 기관 연결 실패\n${
                        errors.joinToString(
                            "\n"
                        )
                    }"

                    else -> "실제 기사 ${sorted.size}건 · 새로고침으로 업데이트"
                }
                sorted.forEach { addNewsCard(it) }
            }
        }.start()
    }

    private fun parseRss(xml: String, source: String): List<News> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val result = mutableListOf<News>()
        var inside = false
        var title = ""
        var link = ""
        var date = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.ROOT)) {
                    "item", "entry" -> {
                        inside = true; title = ""; link = ""; date = ""
                    }

                    "title" -> if (inside) title = parser.nextText().trim()
                    "link" -> if (inside) {
                        val href = parser.getAttributeValue(null, "href")
                        link = if (!href.isNullOrBlank()) href else parser.nextText().trim()
                    }

                    "pubdate", "published", "updated", "dc:date" -> if (inside) date =
                        parser.nextText().trim()
                }
            } else if (event == XmlPullParser.END_TAG &&
                parser.name.lowercase(Locale.ROOT) in listOf("item", "entry")
            ) {
                if (title.isNotBlank() && (link.startsWith("https://") || link.startsWith("http://"))) {
                    result.add(News(title, link, date, source, parseDate(date)))
                }
                inside = false
            }
            event = parser.next()
        }
        return result
    }

    private fun parseDate(raw: String): Long {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                return SimpleDateFormat(format, Locale.ENGLISH).apply { isLenient = false }
                    .parse(raw)?.time ?: 0L
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun addNewsCard(news: News) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
            }
            elevation = dp(2).toFloat()
            isClickable = true
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(news.url)))
                } catch (_: Exception) {
                    Toast.makeText(this@HealthNewsActivity, "원문을 열 수 없어요.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        card.addView(TextView(this).apply {
            text = news.title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(38, 54, 92))
        })
        val formatted = if (news.time > 0L) {
            SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(news.time))
        } else news.date
        card.addView(TextView(this).apply {
            text = "${news.source}  ·  ${formatted.ifBlank { "날짜 미제공" }}  ↗"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(9), 0, 0)
        })
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
