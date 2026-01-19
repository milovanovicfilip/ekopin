package com.example.mobile.ui.geiger

import android.content.Context
import android.view.View
import android.widget.Switch
import android.widget.TextView
import com.example.mobile.R
import com.example.mobile.util.geiger.GeigerCaptureManager
import java.util.Locale

object GeigerSettingsCard {

    fun bind(root: View, context: Context) {
        val tvRange = root.findViewById<TextView>(R.id.tvRange)
        val tvFrequency = root.findViewById<TextView>(R.id.tvFrequency)
        val switchEnabled = root.findViewById<Switch>(R.id.switchEnabled)

        val s = GeigerCaptureManager.getCurrentSettings()
        val capturing = GeigerCaptureManager.isCapturing()

        tvRange.text = String.format(
            Locale.getDefault(),
            "↝ Od %.2f do %.2f",
            s.minCpm,
            s.maxCpm
        )

        tvFrequency.text = buildFrequencyText(context, s.frequencyValue, s.frequencyUnit)
        switchEnabled.isChecked = capturing
    }

    private fun buildFrequencyText(context: Context, value: Int, unit: String): String {
        val u = unit.trim().lowercase(Locale.getDefault())

        val secondUi = context.getString(R.string.second).trim().lowercase(Locale.getDefault())
        val minuteUi = context.getString(R.string.minute).trim().lowercase(Locale.getDefault())
        val hourUi = context.getString(R.string.hour).trim().lowercase(Locale.getDefault())

        val prettyUnit = when (u) {
            "sekund", secondUi -> "sekund"
            "minut", minuteUi -> "minut"
            "ur", hourUi ->"ur"
            else -> "minut"
        }

        return "⏱ Vsakih $value $prettyUnit"
    }
}
