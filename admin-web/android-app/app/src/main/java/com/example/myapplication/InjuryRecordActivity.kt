package com.example.myapplication

import android.content.Intent
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class InjuryRecordActivity : AppCompatActivity() {
    private lateinit var recyclerRecords: RecyclerView
    private lateinit var txtEmptyRecord: TextView
    private val recordList = mutableListOf<InjuryRecord>()
    private lateinit var recordAdapter: InjuryRecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_injury_record)
        findViewById<Button>(R.id.btnBackHome).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCamera).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        recyclerRecords = findViewById(R.id.recyclerRecords)
        txtEmptyRecord = findViewById(R.id.txtEmptyRecord)
        recyclerRecords.layoutManager = LinearLayoutManager(this)
        recordAdapter = InjuryRecordAdapter(recordList)
        recyclerRecords.adapter = recordAdapter
    }

    override fun onResume() {
        super.onResume()
        loadInjuryRecords()
    }

    private fun loadInjuryRecords() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            showEmptyRecord(); return
        }
        FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .collection("injuryRecords")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                recordList.clear()
                val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.KOREA)
                for (document in documents) {
                    val rawConfidence = document.get("confidence")
                    val confidence = when (rawConfidence) {
                        is Number -> rawConfidence.toDouble()
                        is String -> rawConfidence.replace("%", "").toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val messages = document.get("messages") as? List<*>
                    val messagesJson = JSONArray().apply {
                        messages?.forEach { entry ->
                            val map = entry as? Map<*, *> ?: return@forEach
                            put(JSONObject().apply {
                                put("role", map["role"]?.toString().orEmpty())
                                put("content", map["content"]?.toString().orEmpty())
                            })
                        }
                    }.toString()
                    recordList.add(
                        InjuryRecord(
                        documentId = document.id,
                        date = document.getTimestamp("createdAt")?.toDate()
                            ?.let { dateFormat.format(it) } ?: "-",
                        injuryName = document.getString("injuryName") ?: "상처 종류 없음",
                        risk = document.getString("risk") ?: "확인 필요",
                        confidence = confidence,
                        imageUri = document.getString("imageUri") ?: "",
                        emergency = document.getString("emergency") ?: "",
                        firstAid = document.getString("firstAid") ?: "",
                        recordType = document.getString("recordType") ?: "",
                        messagesJson = messagesJson
                    ))
                }
                if (recordList.isEmpty()) showEmptyRecord() else {
                    txtEmptyRecord.visibility = View.GONE
                    recyclerRecords.visibility = View.VISIBLE
                    recordAdapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener {
                showEmptyRecord()
                Toast.makeText(this, "상처 기록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmptyRecord() {
        recyclerRecords.visibility = View.GONE
        txtEmptyRecord.visibility = View.VISIBLE
    }
}
