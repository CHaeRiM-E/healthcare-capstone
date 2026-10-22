package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import org.json.JSONArray

class InjuryRecordDetailActivity : AppCompatActivity() {

    private lateinit var imgDetail: ImageView
    private lateinit var btnDeleteRecord: Button

    private var documentId: String = ""
    private var imageUri: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_injury_record_detail)

        imgDetail = findViewById(R.id.imgDetail)

        val txtDetailDate =
            findViewById<TextView>(R.id.txtDetailDate)

        val txtDetailName =
            findViewById<TextView>(R.id.txtDetailName)

        val txtDetailConfidence =
            findViewById<TextView>(R.id.txtDetailConfidence)

        val txtDetailRisk =
            findViewById<TextView>(R.id.txtDetailRisk)

        val txtDetailEmergency =
            findViewById<TextView>(R.id.txtDetailEmergency)

        val txtDetailFirstAid =
            findViewById<TextView>(R.id.txtDetailFirstAid)

        btnDeleteRecord =
            findViewById(R.id.btnDeleteRecord)

        val btnDetailBack =
            findViewById<Button>(R.id.btnDetailBack)

        documentId =
            intent.getStringExtra("documentId") ?: ""

        val date =
            intent.getStringExtra("date") ?: "-"

        val injuryName =
            intent.getStringExtra("injuryName")
                ?: "상처 종류 없음"

        val risk =
            intent.getStringExtra("risk")
                ?: "확인 필요"

        val confidence =
            intent.getDoubleExtra(
                "confidence",
                0.0
            )

        imageUri =
            intent.getStringExtra("imageUri") ?: ""

        val emergency =
            intent.getStringExtra("emergency")
                ?: "-"

        val firstAid =
            intent.getStringExtra("firstAid")
                ?: "-"

        val isChat = intent.getStringExtra("recordType") == "aiConsultation"
        val messagesJson = intent.getStringExtra("messagesJson").orEmpty()

        txtDetailDate.text =
            "기록 날짜 · $date"

        txtDetailName.text =
            if (isChat) "AI 외상 상담 · $injuryName" else injuryName

        txtDetailRisk.text =
            "위험 수준 · $risk"

        txtDetailConfidence.text =
            if (confidence > 0.0) {
                "AI 탐지 점수 · %.1f%%".format(
                    confidence
                )
            } else {
                "AI 탐지 점수 · -"
            }

        if (isChat) {
            txtDetailEmergency.text = "AI 외상 추가 상담 기록"
            val transcript = try {
                val array = JSONArray(messagesJson)
                (0 until array.length()).joinToString("\n\n") { index ->
                    val item = array.optJSONObject(index)
                    val role = if (item?.optString("role") == "user") "나" else "AI 상담"
                    "$role: ${item?.optString("content").orEmpty()}"
                }.ifBlank { "저장된 상담 내용이 없습니다." }
            } catch (_: Exception) {
                "상담 내용을 표시할 수 없습니다."
            }
            txtDetailFirstAid.text = transcript
        } else {
            txtDetailEmergency.text =
                if (emergency.isBlank()) {
                    "-"
                } else {
                    emergency
                }

            txtDetailFirstAid.text =
                if (firstAid.isBlank()) {
                    "-"
                } else {
                    firstAid
                        .removePrefix("응급 처치 방법")
                        .removePrefix("응급처치 방법")
                        .trim()
                }

        }

        showImage()
        imgDetail.setOnClickListener {

            val zoomImage = ImageView(this).apply {
                setImageDrawable(imgDetail.drawable)
                scaleType = ImageView.ScaleType.MATRIX
            }

            val matrix = Matrix()
            var currentScale = 1f
            var lastX = 0f
            var lastY = 0f
            var isDragging = false

            val scaleDetector = ScaleGestureDetector(
                this,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(
                        detector: ScaleGestureDetector
                    ): Boolean {

                        val newScale = (
                                currentScale * detector.scaleFactor
                                ).coerceIn(1f, 5f)

                        val factor = newScale / currentScale

                        matrix.postScale(
                            factor,
                            factor,
                            detector.focusX,
                            detector.focusY
                        )

                        currentScale = newScale
                        zoomImage.imageMatrix = matrix

                        return true
                    }
                }
            )

            zoomImage.setOnTouchListener { _, event ->

                scaleDetector.onTouchEvent(event)

                when (event.actionMasked) {

                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.x
                        lastY = event.y
                        isDragging = true
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        isDragging = false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (
                            !scaleDetector.isInProgress &&
                            event.pointerCount == 1 &&
                            isDragging &&
                            currentScale > 1f
                        ) {
                            val dx = event.x - lastX
                            val dy = event.y - lastY

                            matrix.postTranslate(dx, dy)
                            zoomImage.imageMatrix = matrix
                        }

                        lastX = event.x
                        lastY = event.y
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                    }

                    MotionEvent.ACTION_POINTER_UP -> {
                        isDragging = false
                    }
                }

                true
            }

            val container = android.widget.FrameLayout(this)

            val height =
                (resources.displayMetrics.heightPixels * 0.65).toInt()

            container.addView(
                zoomImage,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    height
                )
            )

            AlertDialog.Builder(this)
                .setTitle("상처 사진 확대")
                .setView(container)
                .setPositiveButton("닫기", null)
                .show()

            // 사진 전체가 처음부터 보이도록 맞추기
            zoomImage.post {
                val drawable = zoomImage.drawable ?: return@post

                val imageWidth = drawable.intrinsicWidth.toFloat()
                val imageHeight = drawable.intrinsicHeight.toFloat()

                if (imageWidth <= 0f || imageHeight <= 0f) return@post

                val viewWidth = zoomImage.width.toFloat()
                val viewHeight = zoomImage.height.toFloat()

                val fitScale = minOf(
                    viewWidth / imageWidth,
                    viewHeight / imageHeight
                )

                matrix.reset()
                matrix.postScale(fitScale, fitScale)

                matrix.postTranslate(
                    (viewWidth - imageWidth * fitScale) / 2f,
                    (viewHeight - imageHeight * fitScale) / 2f
                )

                currentScale = 1f
                zoomImage.imageMatrix = matrix
            }
        }

        btnDetailBack.setOnClickListener {
            finish()
        }

        btnDeleteRecord.setOnClickListener {
            showDeleteDialog()
        }
    }

    private fun showImage() {

        if (
            imageUri.startsWith("https://") ||
            imageUri.startsWith("http://")
        ) {

            imgDetail.visibility = View.VISIBLE

            Glide.with(this)
                .load(imageUri)
                .fitCenter()
                .into(imgDetail)

        } else {

            imgDetail.visibility = View.GONE
        }
    }

    private fun showDeleteDialog() {

        if (documentId.isBlank()) {

            Toast.makeText(
                this,
                "삭제할 기록 정보를 찾을 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        AlertDialog.Builder(this)
            .setTitle("기록 삭제")
            .setMessage(
                "이 상처 기록을 삭제하시겠습니까?\n" +
                        "삭제한 기록은 복구할 수 없습니다."
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
            FirebaseAuth.getInstance().currentUser

        if (user == null) {

            Toast.makeText(
                this,
                "로그인 정보를 확인할 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        btnDeleteRecord.isEnabled = false
        btnDeleteRecord.text = "삭제 중..."

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("injuryRecords")
            .document(documentId)
            .delete()
            .addOnSuccessListener {

                deleteStoredImage()

                Toast.makeText(
                    this,
                    "기록이 삭제되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
            .addOnFailureListener {

                btnDeleteRecord.isEnabled = true
                btnDeleteRecord.text = "기록 삭제"

                Toast.makeText(
                    this,
                    "기록을 삭제하지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun deleteStoredImage() {

        if (
            !imageUri.startsWith("https://") &&
            !imageUri.startsWith("http://")
        ) {
            return
        }

        try {

            FirebaseStorage.getInstance()
                .getReferenceFromUrl(imageUri)
                .delete()

        } catch (_: Exception) {
            // 사진 삭제 실패가 기록 삭제를 방해하지 않도록 함
        }
    }
}