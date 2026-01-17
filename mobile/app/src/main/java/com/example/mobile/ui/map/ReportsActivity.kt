package com.example.mobile.ui.reports

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobile.databinding.ActivityReportsBinding
import com.example.mobile.data.model.PollutionTag
import com.example.mobile.util.SimPrefs
import com.example.mobile.util.mqtt.MqttProvider
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private lateinit var adapter: ReportsAdapter

    private val myDeviceId = Build.MODEL ?: "android"
    private var showMineOnly = false
    private var severityFilter: String = "Vse"

    private val allItems = ArrayList<PollutionTag>()

    private var mqttListener: ((String, String) -> Unit)? = null
    private var listRespTopic: String? = null
    private var addedTopic: String? = null
    private var deletedTopic: String? = null
    private var mode: String = "real"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = if (SimPrefs.isEnabled(this)) "sim" else "real"
        addedTopic = "ekopin/$mode/pollution_tags/added"
        deletedTopic = "ekopin/$mode/pollution_tags/deleted"

        setupFilters()
        setupRecycler()

        binding.btnBack.setOnClickListener { finish() }

        connectMqttAndLoad()
    }

    private fun setupFilters() {
        binding.btnAll.setOnClickListener {
            showMineOnly = false
            updateFilterButtons()
            applyFilters()
        }
        binding.btnMine.setOnClickListener {
            showMineOnly = true
            updateFilterButtons()
            applyFilters()
        }
        updateFilterButtons()

        val options = listOf("Vse", "Nizka", "Srednja", "Visoka")
        binding.spSeverityFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spSeverityFilter.setSelection(0)
        binding.spSeverityFilter.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                severityFilter = options[position]
                applyFilters()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun updateFilterButtons() {
        binding.btnAll.isEnabled = showMineOnly
        binding.btnMine.isEnabled = !showMineOnly
    }

    private fun setupRecycler() {
        adapter = ReportsAdapter(emptyList(), myDeviceId) { tag ->
            confirmDelete(tag)
        }
        binding.rvReports.layoutManager = LinearLayoutManager(this)
        binding.rvReports.adapter = adapter
    }

    private fun connectMqttAndLoad() {
        val mqtt = MqttProvider.mqtt
        if (mqtt == null || !mqtt.isConnected()) {
            Toast.makeText(this, "MQTT ni povezan", Toast.LENGTH_SHORT).show()
            return
        }

        val reqId = System.currentTimeMillis().toString()
        val resp = "ekopin/$mode/pollution_tags/list/response/$myDeviceId/$reqId"
        val reqTopic = "ekopin/$mode/pollution_tags/list/request"

        listRespTopic = resp

        mqtt.subscribe(resp, qos = 1) { err -> Log.e("REPORTS", "sub list resp fail", err) }

        val listener: (String, String) -> Unit = { topic, payload ->
            try {
                if (topic == "ekopin/$mode/pollution_tags/added") {
                    val it = parseOne(JSONObject(payload))
                    runOnUiThread {
                        upsert(it)
                        applyFilters()
                    }
                } else if (topic == "ekopin/$mode/pollution_tags/deleted") {
                    val o = JSONObject(payload)
                    val id = o.optString("id", "")
                    if (id.isNotBlank()) {
                        runOnUiThread {
                            removeById(id)
                            applyFilters()
                        }
                    }
                } else if (topic == resp) {
                    val o = JSONObject(payload)
                    val arr = o.optJSONArray("items") ?: JSONArray()
                    val list = ArrayList<PollutionTag>(arr.length())
                    for (i in 0 until arr.length()) {
                        list.add(parseOne(arr.getJSONObject(i)))
                    }
                    runOnUiThread {
                        allItems.clear()
                        allItems.addAll(list)
                        applyFilters()
                    }
                }
            } catch (e: Exception) {
                Log.e("REPORTS", "parse fail topic=$topic", e)
            }
        }

        mqtt.addMessageListener(listener)
        mqttListener = listener

        val reqPayload = JSONObject().apply {
            put("deviceId", myDeviceId)
            put("reqId", reqId)
        }.toString()

        mqtt.publish(reqTopic, reqPayload, qos = 1, retained = false) { err ->
            Log.e("REPORTS", "publish list request fail", err)
        }
    }


    private fun confirmDelete(tag: PollutionTag) {
        AlertDialog.Builder(this)
            .setTitle("Brisanje")
            .setMessage("Res želiš izbrisati ta report?")
            .setNegativeButton("Ne", null)
            .setPositiveButton("Da") { _, _ -> publishDelete(tag) }
            .show()
    }

    private fun publishDelete(tag: PollutionTag) {
        val mqtt = MqttProvider.mqtt
        if (mqtt == null || !mqtt.isConnected()) {
            Toast.makeText(this, "MQTT ni povezan", Toast.LENGTH_SHORT).show()
            return
        }

        val deleteTopic = "ekopin/$mode/pollution_tags/delete"
        val payload = JSONObject().apply {
            put("deviceId", myDeviceId)
            put("id", tag.id)
        }.toString()

        mqtt.publish(deleteTopic, payload, qos = 1, retained = false) { err ->
            Log.e("REPORTS", "publish delete failed", err)
        }

        Toast.makeText(this, "Zahteva za brisanje poslana", Toast.LENGTH_SHORT).show()
    }

    private fun applyFilters() {
        val filtered = allItems.asSequence()
            .filter { if (showMineOnly) it.deviceId == myDeviceId else true }
            .filter { if (severityFilter == "Vse") true else it.severity.equals(severityFilter, ignoreCase = true) }
            .sortedByDescending { it.ts }
            .toList()

        adapter.submit(filtered)
    }

    private fun upsert(tag: PollutionTag) {
        val idx = allItems.indexOfFirst { it.id == tag.id }
        if (idx >= 0) allItems[idx] = tag else allItems.add(tag)
    }

    private fun removeById(id: String) {
        allItems.removeAll { it.id == id }
    }

    private fun parseOne(o: JSONObject): PollutionTag {
        val id = o.optString("_id").ifBlank { o.optString("id").ifBlank { o.optLong("ts").toString() } }
        val deviceId = o.optString("deviceId", "")
        val label = o.optString("label", "")
        val description = o.optString("description", "")
        val sevRaw = o.optString("severity", "").trim()
        val severity = if (sevRaw.isBlank() || sevRaw.equals("null", true)) "Nizka" else sevRaw
        val lat = o.optDouble("lat", 0.0)
        val lng = o.optDouble("lng", 0.0)
        val ts = o.optLong("ts", System.currentTimeMillis())
        val modeVal = o.optString("mode", mode)
        val imageMime = o.optString("imageMime", null)
        val imageBase64 = o.optString("imageBase64", null)

        return PollutionTag(
            id = id,
            deviceId = deviceId,
            label = label,
            description = description,
            severity = severity.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
            lat = lat,
            lng = lng,
            ts = ts,
            mode = modeVal,
            imageMime = imageMime,
            imageBase64 = imageBase64
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        val mqtt = MqttProvider.mqtt
        mqttListener?.let { l -> mqtt?.removeMessageListener(l) }
        mqttListener = null

        listRespTopic?.let { t -> mqtt?.unsubscribe(t) { } }
        listRespTopic = null
    }

}
