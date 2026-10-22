package com.example.myapplication

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class HealthBenefitsActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val year = Calendar.getInstance().get(Calendar.YEAR)
    private val completed = mutableSetOf<String>()
    private var filter = "all"
    private lateinit var completedText: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var filters: LinearLayout

    private val navy = Color.rgb(30, 43, 64)
    private val muted = Color.rgb(106, 121, 145)
    private val blue = Color.rgb(86, 132, 215)
    private val items = listOf(
        Benefit(
            "general",
            "국가 일반건강검진",
            "올해 검진 대상 여부를 확인해 보세요",
            "https://www.nhis.or.kr",
            "✚",
            0xFFEAF1FF.toInt(),
            0xFF5684D7.toInt()
        ),
        Benefit(
            "dental",
            "치과 스케일링",
            "올해 건강보험 적용 여부를 확인해 보세요",
            "https://www.nhis.or.kr",
            "✦",
            0xFFE9F7F2.toInt(),
            0xFF3F9C80.toInt()
        ),
        Benefit(
            "cancer",
            "국가암검진",
            "연령·성별에 따른 대상 여부를 확인해요",
            "https://www.nhis.or.kr",
            "♥",
            0xFFFFF1F3.toInt(),
            0xFFE07C93.toInt()
        ),
        Benefit(
            "vaccine",
            "예방접종",
            "접종 이력과 지원 대상을 확인해요",
            "https://nip.kdca.go.kr",
            "✳",
            0xFFFFF5E8.toInt(),
            0xFFD99A43.toInt()
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFFF8FAFD.toInt())
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(36))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(text("‹  뒤로", 16f, muted, true).apply {
            setPadding(0, dp(4), 0, dp(20))
            setOnClickListener { finish() }
        })
        root.addView(text("MY HEALTH", 12f, blue, true))
        root.addView(text("나의 건강관리", 27f, navy, true).apply { setPadding(0, dp(5), 0, dp(5)) })
        root.addView(text("올해의 건강 일정과 혜택을 확인해요", 14f, muted, false))

        val benefits = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFFEAF2FF.toInt(), 22)
            setPadding(dp(18), dp(20), dp(18), dp(18))
        }
        benefits.addView(text("$year 건강 혜택", 13f, 0xFF5276B6.toInt(), true))
        benefits.addView(text("놓치기 쉬운 혜택을 확인하세요", 19f, 0xFF203A63.toInt(), true).apply {
            setPadding(0, dp(7), 0, dp(4))
        })
        benefits.addView(text("국가검진 · 스케일링 · 예방접종", 13f, 0xFF5B7295.toInt(), false))
        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            Triple("✚", "건강검진", "https://www.nhis.or.kr"),
            Triple("✦", "스케일링", "https://www.nhis.or.kr"),
            Triple("✳", "예방접종", "https://nip.kdca.go.kr")
        ).forEachIndexed { index, entry ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(Color.WHITE, 15)
                setPadding(dp(4), dp(13), dp(4), dp(12))
                setOnClickListener { openUrl(entry.third) }
            }
            tile.addView(text(entry.first, 23f, blue, true).apply { gravity = Gravity.CENTER })
            tile.addView(text(entry.second, 12f, 0xFF344C72.toInt(), true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, 0)
            })
            shortcuts.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
        benefits.addView(shortcuts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(19) })
        root.addView(benefits, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(23) })

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(text("나의 건강 체크리스트", 20f, navy, true))
        titleCol.addView(text("완료한 항목을 직접 체크해 보세요", 12f, muted, false).apply {
            setPadding(0, dp(6), 0, 0)
        })
        heading.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
        completedText = text("0/${items.size} 완료", 12f, blue, true).apply {
            background = rounded(0xFFEAF1FF.toInt(), 20)
            setPadding(dp(11), dp(8), dp(11), dp(8))
        }
        heading.addView(completedText)
        root.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(29) })

        filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(filters, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(15) })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })

        root.addView(action("✨  나에게 맞는 건강 혜택 확인") {
            startActivity(Intent(this, PersonalHealthActivity::class.java))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
        root.addView(action("📅  건강 캘린더 열기") {
            startActivity(Intent(this, HealthCalendarActivity::class.java))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
        root.addView(
            text(
                "체크 상태는 연도별로 저장돼요. 검진 대상·지원 자격은 공식 기관에서 확인해 주세요.",
                12f,
                muted,
                false
            ).apply {
                setPadding(dp(3), dp(17), dp(3), 0)
            })

        renderItems()
        loadProgress()
    }

    private fun renderFilters() {
        filters.removeAllViews()
        listOf(
            Triple("all", "전체", items.size),
            Triple("todo", "확인 필요", items.size - completed.size),
            Triple("done", "완료", completed.size)
        ).forEachIndexed { index, option ->
            val selected = filter == option.first
            val label = text(
                "${option.second} ${option.third}",
                13f,
                if (selected) Color.WHITE else muted,
                selected
            ).apply {
                gravity = Gravity.CENTER
                background = rounded(if (selected) blue else Color.WHITE, 19)
                setPadding(dp(13), dp(10), dp(13), dp(10))
                setOnClickListener {
                    filter = option.first
                    renderItems()
                }
            }
            filters.addView(label, LinearLayout.LayoutParams(-2, -2).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
    }

    private fun renderItems() {
        updateCount()
        renderFilters()
        listContainer.removeAllViews()
        val visible = items.filter {
            filter == "all" || (filter == "done" && it.id in completed) || (filter == "todo" && it.id !in completed)
        }
        if (visible.isEmpty()) {
            listContainer.addView(text("표시할 항목이 없어요.", 14f, muted, false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(30), dp(10), dp(30))
            })
            return
        }
        visible.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Color.WHITE, 19)
                setPadding(dp(15), dp(15), dp(15), dp(13))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val icon = text(item.symbol, 23f, item.iconColor, true).apply {
                gravity = Gravity.CENTER
                background = rounded(item.iconBackground, 14)
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(53), dp(53)))
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(text(item.title, 16f, navy, true))
            content.addView(text(item.description, 12f, muted, false).apply {
                setPadding(0, dp(5), 0, 0)
            })
            row.addView(content, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            val check = CheckBox(this).apply {
                buttonTintList = ColorStateList.valueOf(0xFF3F9C80.toInt())
                isChecked = item.id in completed
                contentDescription = "${item.title} 완료 여부"
                setOnCheckedChangeListener { _, checked ->
                    if (checked) completed.add(item.id) else completed.remove(item.id)
                    updateCount()
                    saveProgress()
                    renderItems()
                }
            }
            row.addView(check)
            card.addView(row)
            val link = text("공식 정보 확인  ↗", 13f, blue, true).apply {
                setPadding(dp(65), dp(11), 0, dp(2))
                setOnClickListener { openUrl(item.url) }
            }
            card.addView(link)
            listContainer.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun updateCount() {
        completedText.text = "${completed.size}/${items.size} 완료"
    }

    private fun progressRef() = auth.currentUser?.uid?.let { uid ->
        db.collection("users").document(uid).collection("healthBenefitProgress")
            .document(year.toString())
    }

    private fun loadProgress() {
        val ref = progressRef() ?: run {
            Toast.makeText(this, "로그인하면 체크리스트가 저장돼요.", Toast.LENGTH_LONG).show()
            return
        }
        ref.get().addOnSuccessListener { snapshot ->
            completed.clear()
            val saved = snapshot.get("completedIds") as? List<*>
            completed.addAll(saved?.filterIsInstance<String>() ?: emptyList())
            renderItems()
        }.addOnFailureListener {
            Toast.makeText(this, "체크리스트를 불러오지 못했어요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProgress() {
        val ref = progressRef() ?: return
        ref.set(mapOf("year" to year, "completedIds" to completed.toList()))
            .addOnFailureListener {
                Toast.makeText(this, "체크 상태 저장에 실패했어요.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "브라우저를 열 수 없어요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun action(label: String, onClick: () -> Unit): TextView =
        text(label, 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(17), dp(12), dp(17))
            background = rounded(blue, 17)
            setOnClickListener { onClick() }
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class Benefit(
        val id: String,
        val title: String,
        val description: String,
        val url: String,
        val symbol: String,
        val iconBackground: Int,
        val iconColor: Int
    )
}
