package com.example.mobile.ui.sensors

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mobile.R
import com.example.mobile.data.model.Sensor

class SensorsAdapter(
    private val sensors: List<Sensor>,
    private val onClick: (Sensor) -> Unit,
    private val onToggle: (Sensor, Boolean) -> Unit
) : RecyclerView.Adapter<SensorsAdapter.SensorViewHolder>() {

    inner class SensorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val range: TextView = view.findViewById(R.id.tvRange)
        val frequency: TextView = view.findViewById(R.id.tvFrequency)
        val location: TextView = view.findViewById(R.id.tvLocation)
        val enabled: Switch = view.findViewById(R.id.switchEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.sensor_card, parent, false)
        return SensorViewHolder(view)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        val sensor = sensors[position]

        holder.title.text = sensor.name
        holder.range.text = sensor.range
        holder.frequency.text = sensor.frequency
        holder.location.text = sensor.location

        // Skrij range če je prazen (real mode)
        if (sensor.range.isBlank()) {
            holder.range.visibility = View.GONE
        } else {
            holder.range.visibility = View.VISIBLE
        }

        holder.enabled.setOnCheckedChangeListener(null)
        holder.enabled.isChecked = sensor.enabled
        holder.enabled.setOnCheckedChangeListener { _, isChecked ->
            onToggle(sensor, isChecked)
        }

        holder.itemView.setOnClickListener {
            onClick(sensor)
        }
    }

    override fun getItemCount(): Int = sensors.size
}
