package com.example.mobile.ui.map

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.mobile.R
import com.example.mobile.map.MapGdxApp
import com.example.mobile.ui.reports.ReportsActivity
import com.example.mobile.util.SimPrefs
import com.example.mobile.util.mqtt.MqttProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MapFragment : AndroidFragmentApplication() {

    private var pickedLat: Float? = null
    private var pickedLon: Float? = null

    private var mqttListener: ((String, String) -> Unit)? = null
    private var listRespTopic: String? = null
    private var mode: String = "real"

    private var appRef: MapGdxApp? = null
    private val tagById = HashMap<String, JSONObject>()

    @Volatile private var ignorePickUi = false

    private val reportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        clearPick()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_map, container, false)

        val card = root.findViewById<View>(R.id.card_pick_location)
        val tvCoords = root.findViewById<TextView>(R.id.tv_pick_coords)

        val btnContinue = root.findViewById<View>(R.id.btn_continue)
        val btnCancel = root.findViewById<View>(R.id.btn_cancel_pick)
        val btnList = root.findViewById<View>(R.id.btn_home)
        val btnAddReport = root.findViewById<View>(R.id.btn_layers)

        mode = if (SimPrefs.isEnabled(requireContext())) "sim" else "real"

        val greenBytes = drawableToPngBytes(R.drawable.marker)
        val redPickBytes = drawableToPngBytes(R.drawable.marker_moveable)

        val createdApp = MapGdxApp(
            onPickLocation = { lat, lon ->
                if (ignorePickUi) return@MapGdxApp
                pickedLat = lat
                pickedLon = lon
                activity?.runOnUiThread {
                    tvCoords.text = "lat: ${"%.6f".format(lat)}, lon: ${"%.6f".format(lon)}"
                    card.visibility = View.VISIBLE
                }
            },
            onDotClick = { id ->
                activity?.runOnUiThread { showDotDialog(id) }
            },
            greenMarkerPng = greenBytes,
            redMoveableMarkerPng = redPickBytes
        )

        appRef = createdApp

        val config = AndroidApplicationConfiguration().apply { useImmersiveMode = false }
        val gdxView = initializeForView(createdApp, config)
        root.findViewById<ViewGroup>(R.id.gdx_container).addView(
            gdxView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        setupMqtt(createdApp)
        btnCancel.setOnClickListener { clearPick() }

        btnContinue.setOnClickListener {
            val lat = pickedLat ?: return@setOnClickListener
            val lon = pickedLon ?: return@setOnClickListener
            ignorePickUi = true

            val intent = Intent(requireContext(), PollutionReportActivity::class.java).apply {
                putExtra("lat", lat.toDouble())
                putExtra("lon", lon.toDouble())
                putExtra("ts", System.currentTimeMillis())
            }
            reportLauncher.launch(intent)
        }

        btnAddReport.setOnClickListener {
            ignorePickUi = true
            val intent = Intent(requireContext(), PollutionReportActivity::class.java).apply {
                putExtra("ts", System.currentTimeMillis())
            }
            reportLauncher.launch(intent)
        }

        btnList.setOnClickListener {
            startActivity(Intent(requireContext(), ReportsActivity::class.java))
        }

        card.visibility = View.GONE
        return root
    }

    private fun setupMqtt(createdApp: MapGdxApp) {
        val mqtt = MqttProvider.mqtt
        if (mqtt == null || !mqtt.isConnected()) return

        val deviceId = android.os.Build.MODEL ?: "android"
        val reqId = System.currentTimeMillis().toString()

        val listReqTopic = "ekopin/$mode/pollution_tags/list/request"
        val respTopic = "ekopin/$mode/pollution_tags/list/response/$deviceId/$reqId"
        listRespTopic = respTopic

        mqtt.subscribe(respTopic, qos = 1) { err ->
            Log.e("MAP", "sub list resp fail", err)
        }

        val listener: (String, String) -> Unit = { topic, payload ->
            try {
                val currentMode = if (SimPrefs.isEnabled(requireContext())) "sim" else "real"
                val addedTopicNow = "ekopin/$currentMode/pollution_tags/added"
                val deletedTopicNow = "ekopin/$currentMode/pollution_tags/deleted"

                when (topic) {
                    addedTopicNow -> {
                        val o = JSONObject(payload)
                        val id = o.optString("_id").ifBlank { o.optLong("ts").toString() }
                        val lat = o.optDouble("lat").toFloat()
                        val lon = o.optDouble("lng").toFloat()
                        tagById[id] = o

                        com.badlogic.gdx.Gdx.app.postRunnable {
                            createdApp.addDot(id, lat, lon)
                        }
                    }

                    deletedTopicNow -> {
                        val o = JSONObject(payload)
                        val id = o.optString("id", "")
                        if (id.isNotBlank()) {
                            tagById.remove(id)
                            com.badlogic.gdx.Gdx.app.postRunnable {
                                createdApp.removeDot(id)
                            }
                        }
                    }

                    respTopic -> {
                        val o = JSONObject(payload)
                        val items = o.optJSONArray("items")

                        if (items != null) {
                            val dots = ArrayList<Triple<String, Float, Float>>(items.length())
                            val newMap = HashMap<String, JSONObject>(items.length())

                            for (i in 0 until items.length()) {
                                val it = items.getJSONObject(i)
                                val id = it.optString("_id").ifBlank { it.optLong("ts").toString() }
                                val lat = it.optDouble("lat").toFloat()
                                val lon = it.optDouble("lng").toFloat()
                                dots.add(Triple(id, lat, lon))
                                newMap[id] = it
                            }

                            tagById.clear()
                            tagById.putAll(newMap)

                            com.badlogic.gdx.Gdx.app.postRunnable {
                                createdApp.setDots(dots)
                            }
                        } else {
                            Log.w("MAP", "Response has no 'items': $payload")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MAP", "parse fail topic=$topic", e)
            }
        }

        mqtt.addMessageListener(listener)
        mqttListener = listener

        val reqPayload = JSONObject().apply {
            put("deviceId", deviceId)
            put("reqId", reqId)
        }.toString()

        mqtt.publish(listReqTopic, reqPayload, qos = 1, retained = false) { err ->
            Log.e("MAP", "publish list request fail", err)
        }
    }

    private fun clearPick() {
        ignorePickUi = true
        pickedLat = null
        pickedLon = null
        view?.findViewById<View>(R.id.card_pick_location)?.visibility = View.GONE

        appRef?.let { a ->
            com.badlogic.gdx.Gdx.app.postRunnable { a.clearPickDot() }
        }

        ignorePickUi = false
    }

    private fun showDotDialog(id: String) {
        val o = tagById[id] ?: run {
            android.widget.Toast.makeText(requireContext(), "Ni podatkov za izbrano točko.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val title = o.optString("label", "Report")
        val desc = o.optString("description", "")
        val severity = o.optString("severity", "Nizka")
        val imageBase64 = o.optString("imageBase64", "")

        val dialogView = layoutInflater.inflate(R.layout.dialog_dot_details, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvSeverity = dialogView.findViewById<TextView>(R.id.tvSeverity)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tvDesc)
        val ivImage = dialogView.findViewById<ImageView>(R.id.ivImage)

        if (tvTitle == null || tvSeverity == null || tvDesc == null || ivImage == null) {
            Log.e("MAP", "Dialog layout IDs not found. Check dialog_dot_details.xml ids.")
            return
        }

        tvTitle.text = title
        tvSeverity.text = "Nujnost: $severity"
        tvDesc.text = if (desc.isBlank()) "(Brez opisa)" else desc

        val bmp = decodeBase64ToBitmap(imageBase64)
        if (bmp != null) {
            ivImage.setImageBitmap(bmp)
            ivImage.visibility = View.VISIBLE
        } else {
            ivImage.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Zapri", null)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun decodeBase64ToBitmap(b64: String?): android.graphics.Bitmap? {
        val s = b64?.trim().orEmpty()
        if (s.isBlank()) return null
        return try {
            val bytes = Base64.decode(s, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToPngBytes(drawableResId: Int): ByteArray? {
        return try {
            val bmp = BitmapFactory.decodeResource(resources, drawableResId) ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        val mqtt = MqttProvider.mqtt
        mqttListener?.let { l -> mqtt?.removeMessageListener(l) }
        mqttListener = null

        listRespTopic?.let { t -> mqtt?.unsubscribe(t) { } }
        listRespTopic = null

        appRef = null
    }

    override fun exit() {}
}
