package com.example.myapplication

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class InternalRecord(
    val documentId: String = "",
    val date: String = "",
    val title: String = "",
    val firstMessage: String = "",
    val risk: String = "",
    val consultationStatus: String = "상담 완료",
    val messages: ArrayList<HashMap<String, String>> = arrayListOf()
)

class InternalRecordAdapter(
    private val records: List<InternalRecord>
) : RecyclerView.Adapter<InternalRecordAdapter.RecordViewHolder>() {

    class RecordViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val txtInternalDate: TextView =
            itemView.findViewById(R.id.txtInternalDate)

        val txtInternalTitle: TextView =
            itemView.findViewById(R.id.txtInternalTitle)

        val txtInternalRisk: TextView =
            itemView.findViewById(R.id.txtInternalRisk)

        val txtInternalPreview: TextView =
            itemView.findViewById(R.id.txtInternalPreview)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecordViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_internal_record,
                    parent,
                    false
                )

        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: RecordViewHolder,
        position: Int
    ) {

        val record =
            records[position]

        holder.txtInternalDate.text =
            record.date

        holder.txtInternalTitle.text =
            record.title

        holder.txtInternalRisk.text =
            "${record.consultationStatus}  ·  응급도 ${record.risk}"

        holder.txtInternalPreview.text =
            "첫 증상 · ${record.firstMessage}"

        // 기록을 누르면 상세 화면으로 이동
        holder.itemView.setOnClickListener {

            val context =
                holder.itemView.context

            val intent =
                Intent(
                    context,
                    InternalRecordDetailActivity::class.java
                )

            intent.putExtra(
                "documentId",
                record.documentId
            )

            intent.putExtra(
                "date",
                record.date
            )

            intent.putExtra(
                "title",
                record.title
            )

            intent.putExtra(
                "risk",
                record.risk
            )

            intent.putExtra("consultationStatus", record.consultationStatus)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return records.size
    }
}