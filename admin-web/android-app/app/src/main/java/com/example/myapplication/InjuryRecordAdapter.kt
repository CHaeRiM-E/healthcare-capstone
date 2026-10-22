package com.example.myapplication

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class InjuryRecord(
    val documentId: String = "",
    val date: String = "",
    val injuryName: String = "",
    val risk: String = "",
    val confidence: Double = 0.0,
    val imageUri: String = "",
    val emergency: String = "",
    val firstAid: String = "",
    val recordType: String = "",
    val messagesJson: String = ""
)

class InjuryRecordAdapter(private val records: List<InjuryRecord>) :
    RecyclerView.Adapter<InjuryRecordAdapter.RecordViewHolder>() {

    class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtRecordDate: TextView = itemView.findViewById(R.id.txtRecordDate)
        val txtRecordName: TextView = itemView.findViewById(R.id.txtRecordName)
        val txtRecordRisk: TextView = itemView.findViewById(R.id.txtRecordRisk)
        val txtRecordConfidence: TextView = itemView.findViewById(R.id.txtRecordConfidence)
        val imgRecord: ImageView = itemView.findViewById(R.id.imgRecord)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder =
        RecordViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_injury_record, parent, false)
        )

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]
        val isChat = record.recordType == "aiConsultation"
        holder.txtRecordDate.text = record.date
        holder.txtRecordName.text =
            if (isChat) "AI 외상 상담 · ${record.injuryName}" else record.injuryName
        holder.txtRecordRisk.text =
            if (isChat) "상담 응급도 : ${record.risk}" else "위험 수준 : ${record.risk}"
        holder.txtRecordConfidence.text = when {
            record.confidence > 0.0 -> "AI 탐지 점수 : %.1f%%".format(record.confidence)
            isChat -> "AI 추가 상담 기록"
            else -> "AI 탐지 점수 : -"
        }
        Glide.with(holder.itemView.context).clear(holder.imgRecord)
        holder.imgRecord.setImageDrawable(null)
        if (record.imageUri.startsWith("https://") || record.imageUri.startsWith("http://")) {
            holder.imgRecord.visibility = View.VISIBLE
            Glide.with(holder.itemView.context).load(record.imageUri).centerCrop()
                .into(holder.imgRecord)
        } else {
            holder.imgRecord.visibility = View.GONE
        }
        holder.itemView.setOnClickListener {
            val intent =
                Intent(holder.itemView.context, InjuryRecordDetailActivity::class.java).apply {
                    putExtra("documentId", record.documentId)
                    putExtra("date", record.date)
                    putExtra("injuryName", record.injuryName)
                    putExtra("risk", record.risk)
                    putExtra("confidence", record.confidence)
                    putExtra("imageUri", record.imageUri)
                    putExtra("emergency", record.emergency)
                    putExtra("firstAid", record.firstAid)
                    putExtra("recordType", record.recordType)
                    putExtra("messagesJson", record.messagesJson)
                }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = records.size
}
