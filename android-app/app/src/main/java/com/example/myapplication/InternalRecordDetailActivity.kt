package com.example.myapplication

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.text.HtmlCompat

class InternalRecordDetailActivity : AppCompatActivity() {

    private lateinit var detailChatContainer: LinearLayout

    private var documentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_internal_record_detail)

        val btnDetailBack =
            findViewById<Button>(R.id.btnDetailBack)

        val btnBackToList =
            findViewById<Button>(R.id.btnBackToList)

        val btnDeleteRecord =
            findViewById<Button>(R.id.btnDeleteRecord)

        val txtDetailDate =
            findViewById<TextView>(R.id.txtDetailDate)

        val txtDetailTitle =
            findViewById<TextView>(R.id.txtDetailTitle)

        val txtDetailRisk =
            findViewById<TextView>(R.id.txtDetailRisk)

        detailChatContainer =
            findViewById(R.id.detailChatContainer)

        // 목록 화면에서 전달받은 정보
        documentId =
            intent.getStringExtra("documentId")
                ?: ""

        val date =
            intent.getStringExtra("date")
                ?: "-"

        val title =
            intent.getStringExtra("title")
                ?: "내상 상담"

        val risk =
            intent.getStringExtra("risk")
                ?: "미평가"

        txtDetailDate.text =
            "상담 날짜 · $date"

        txtDetailTitle.text =
            title

        txtDetailRisk.text =
            "${intent.getStringExtra("consultationStatus") ?: "상담 완료"} · 응급도 ${if (risk == "상담 중") "미평가" else risk}"

        // Firestore에서 전체 상담 내용 불러오기
        loadChatMessages()

        btnDetailBack.setOnClickListener {
            finish()
        }

        btnBackToList.setOnClickListener {
            finish()
        }

        btnDeleteRecord.setOnClickListener {
            confirmDelete()
        }
    }

    private fun loadChatMessages() {

        val user =
            FirebaseAuth.getInstance()
                .currentUser

        if (user == null) {

            Toast.makeText(
                this,
                "로그인 정보를 확인할 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (documentId.isBlank()) {

            Toast.makeText(
                this,
                "상담 기록을 확인할 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("internalRecords")
            .document(documentId)
            .get()
            .addOnSuccessListener { document ->

                detailChatContainer.removeAllViews()

                val rawMessages =
                    document.get("messages")

                if (rawMessages is List<*>) {

                    for (item in rawMessages) {

                        if (item !is Map<*, *>) {
                            continue
                        }

                        val role =
                            item["role"]
                                ?.toString()
                                ?: ""

                        val content =
                            item["content"]
                                ?.toString()
                                ?: ""

                        if (content.isBlank()) {
                            continue
                        }

                        if (role == "user") {

                            addUserMessage(content)

                        } else if (role == "assistant") {

                            addBotMessage(content)
                        }
                    }
                }

                if (detailChatContainer.childCount == 0) {

                    val emptyText =
                        TextView(this)

                    emptyText.text =
                        "저장된 상담 내용이 없습니다."

                    emptyText.textSize =
                        15f

                    emptyText.setTextColor(
                        getColor(
                            android.R.color.darker_gray
                        )
                    )

                    detailChatContainer.addView(
                        emptyText
                    )
                }
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "상담 내용을 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun addUserMessage(
        message: String
    ) {

        val textView =
            TextView(this)

        textView.text =
            "나\n$message"

        textView.textSize =
            16f

        textView.setTextColor(
            getColor(
                android.R.color.white
            )
        )

        textView.setBackgroundResource(
            R.drawable.bg_chat_user
        )

        textView.setPadding(
            30,
            20,
            30,
            20
        )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.gravity =
            Gravity.END

        params.setMargins(
            80,
            10,
            0,
            10
        )

        textView.layoutParams =
            params

        detailChatContainer.addView(
            textView
        )
    }

    private fun addBotMessage(
        message: String
    ) {

        val textView =
            TextView(this)

        val formattedMessage =
            message
                .replace(
                    Regex("\\*\\*(.+?)\\*\\*"),
                    "<b>$1</b>"
                )
                .replace(
                    "\n",
                    "<br>"
                )

        textView.text =
            HtmlCompat.fromHtml(
                "<b>AI 상담</b><br>$formattedMessage",
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )

        textView.textSize =
            16f

        textView.setTextColor(
            getColor(
                android.R.color.black
            )
        )

        textView.setBackgroundResource(
            R.drawable.bg_chat_bot
        )

        textView.setPadding(
            30,
            20,
            30,
            20
        )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.gravity =
            Gravity.START

        params.setMargins(
            0,
            10,
            80,
            10
        )

        textView.layoutParams =
            params

        detailChatContainer.addView(
            textView
        )
    }

    private fun confirmDelete() {

        AlertDialog.Builder(this)
            .setTitle("기록 삭제")
            .setMessage(
                "이 내상 상담 기록을 삭제할까요?"
            )
            .setNegativeButton(
                "취소",
                null
            )
            .setPositiveButton(
                "삭제"
            ) { _, _ ->

                deleteRecord()
            }
            .show()
    }

    private fun deleteRecord() {

        val user =
            FirebaseAuth.getInstance()
                .currentUser

        if (user == null) {

            Toast.makeText(
                this,
                "로그인 정보를 확인할 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (documentId.isBlank()) {
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("internalRecords")
            .document(documentId)
            .delete()
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "상담 기록이 삭제되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "상담 기록을 삭제하지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}