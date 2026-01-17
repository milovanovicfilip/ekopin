package com.example.mobile.ui.reports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mobile.R
import com.example.mobile.data.model.PollutionTag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsAdapter(
    private var items: List<PollutionTag>,
    private val myDeviceId: String,
    private val onLongPressDelete: (PollutionTag) -> Unit
) : RecyclerView.Adapter<ReportsAdapter.VH>() {

    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvLabel: TextView = v.findViewById(R.id.tvTitle)
        val tvSeverity: TextView = v.findViewById(R.id.tvSeverity)
        val tvMeta: TextView = v.findViewById(R.id.tvMeta)
        val tvDesc: TextView = v.findViewById(R.id.tvDesc)
    }

    fun submit(list: List<PollutionTag>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tag = items[position]

        holder.tvLabel.text = tag.label
        holder.tvSeverity.text = "Nujnost: ${tag.severity}"

        val time = df.format(Date(tag.ts))
        holder.tvMeta.text = "$time • ${"%.5f".format(tag.lat)}, ${"%.5f".format(tag.lng)}"

        holder.tvDesc.text = tag.description.ifBlank { "(Brez opisa)" }

        val isMine = tag.deviceId == myDeviceId
        holder.itemView.setOnLongClickListener {
            if (isMine) onLongPressDelete(tag)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}
