package com.example.myapplication

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class HospitalAdapter(
    private val hospitals: List<Hospital>,
    private val onRouteClick: (Hospital) -> Unit,
    private val onCallClick: (Hospital) -> Unit,
    private val onHospitalClick: (Hospital) -> Unit
) : RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder>() {

    class HospitalViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val txtHospitalName: TextView =
            itemView.findViewById(
                R.id.txtHospitalName
            )

        val txtHospitalDistance: TextView =
            itemView.findViewById(
                R.id.txtHospitalDistance
            )

        val txtHospitalAddress: TextView =
            itemView.findViewById(
                R.id.txtHospitalAddress
            )

        val txtHospitalCategory: TextView =
            itemView.findViewById(
                R.id.txtHospitalCategory
            )

        val btnRoute: Button =
            itemView.findViewById(
                R.id.btnRoute
            )

        val btnCall: Button =
            itemView.findViewById(
                R.id.btnCall
            )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HospitalViewHolder {

        val view =
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_hospital,
                    parent,
                    false
                )

        return HospitalViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: HospitalViewHolder,
        position: Int
    ) {

        val hospital = hospitals[position]
        holder.itemView.setOnClickListener {
            onHospitalClick(hospital)
        }
        holder.itemView.setOnClickListener {
            onHospitalClick(hospital)
        }

        holder.txtHospitalName.text =
            hospital.name

        holder.txtHospitalAddress.text =
            hospital.address.ifBlank {
                "주소 정보 없음"
            }

        holder.txtHospitalCategory.text =
            simplifyCategory(
                hospital.category
            )

        holder.txtHospitalDistance.text =
            formatDistance(
                hospital.distance
            )

        holder.btnRoute.setOnClickListener {
            onRouteClick(hospital)
        }

        if (hospital.phone.isBlank()) {

            holder.btnCall.isEnabled = false
            holder.btnCall.text = "번호 없음"

            holder.btnCall.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor("#BBBBBB")
                )

        } else {

            holder.btnCall.isEnabled = true
            holder.btnCall.text = "전화"

            holder.btnCall.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor("#5F86F2")
                )

            holder.btnCall.setOnClickListener {
                onCallClick(hospital)
            }
        }
    }

    override fun getItemCount(): Int {
        return hospitals.size
    }

    private fun formatDistance(
        distance: Int
    ): String {

        return if (distance >= 1000) {

            String.format(
                Locale.KOREA,
                "거리 %.1fkm",
                distance / 1000.0
            )

        } else {

            "거리 ${distance}m"
        }
    }

    private fun simplifyCategory(
        category: String
    ): String {

        if (category.isBlank()) {
            return "병원"
        }

        val parts =
            category.split(">")

        return parts.last().trim()
    }
}