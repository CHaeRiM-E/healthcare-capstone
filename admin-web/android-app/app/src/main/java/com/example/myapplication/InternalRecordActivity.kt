package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class InternalRecordActivity : AppCompatActivity() {

    private lateinit var recyclerRecords: RecyclerView
    private lateinit var txtEmptyRecord: TextView

    private val recordList =
        mutableListOf<InternalRecord>()

    private lateinit var recordAdapter:
            InternalRecordAdapter

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_internal_record
        )

        val btnBack =
            findViewById<Button>(
                R.id.btnBack
            )

        recyclerRecords =
            findViewById(
                R.id.recyclerRecords
            )

        txtEmptyRecord =
            findViewById(
                R.id.txtEmptyRecord
            )

        recyclerRecords.layoutManager =
            LinearLayoutManager(this)

        recordAdapter =
            InternalRecordAdapter(recordList)

        recyclerRecords.adapter =
            recordAdapter

        btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()

        loadInternalRecords()
    }

    private fun loadInternalRecords() {

        val user =
            FirebaseAuth.getInstance()
                .currentUser

        if (user == null) {
            showEmptyRecord()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("internalRecords")
            .orderBy(
                "createdAt",
                Query.Direction.DESCENDING
            )
            .get()
            .addOnSuccessListener { documents ->

                recordList.clear()

                val dateFormat =
                    SimpleDateFormat(
                        "yy-MM-dd HH:mm",
                        Locale.KOREA
                    )

                for (document in documents) {

                    val documentId =
                        document.id

                    val title =
                        document.getString("title")
                            ?: "내상 상담"

                    val firstMessage =
                        document.getString(
                            "firstMessage"
                        )
                            ?: ""

                    val risk =
                        document.getString("risk")
                            ?: "미평가"

                    val timestamp =
                        document.getTimestamp(
                            "createdAt"
                        )

                    val date =
                        if (timestamp != null) {

                            dateFormat.format(
                                timestamp.toDate()
                            )

                        } else {

                            "-"
                        }

                    val messages =
                        arrayListOf<
                                HashMap<String, String>
                                >()

                    val rawMessages =
                        document.get("messages")

                    if (rawMessages is List<*>) {

                        for (item in rawMessages) {

                            if (item is Map<*, *>) {

                                val role =
                                    item["role"]
                                        ?.toString()
                                        ?: ""

                                val content =
                                    item["content"]
                                        ?.toString()
                                        ?: ""

                                messages.add(
                                    hashMapOf(
                                        "role" to role,
                                        "content" to content
                                    )
                                )
                            }
                        }
                    }

                    recordList.add(
                        InternalRecord(
                            documentId =
                                documentId,

                            date =
                                date,

                            title =
                                title,

                            firstMessage =
                                firstMessage,

                            risk =
                                if (risk == "상담 중") "미평가" else risk,

                            consultationStatus =
                                document.getString("consultationStatus") ?: "상담 완료",

                            messages =
                                messages
                        )
                    )
                }

                if (recordList.isEmpty()) {

                    showEmptyRecord()

                } else {

                    txtEmptyRecord.visibility =
                        View.GONE

                    recyclerRecords.visibility =
                        View.VISIBLE

                    recordAdapter
                        .notifyDataSetChanged()
                }
            }
            .addOnFailureListener {

                showEmptyRecord()

                Toast.makeText(
                    this,
                    "내상 상담 기록을 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showEmptyRecord() {

        recyclerRecords.visibility =
            View.GONE

        txtEmptyRecord.visibility =
            View.VISIBLE
    }
}