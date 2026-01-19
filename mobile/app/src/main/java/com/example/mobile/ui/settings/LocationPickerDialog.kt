package com.example.mobile.ui.settings

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.mobile.R
import com.example.mobile.databinding.DialogLocationPickerBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay

class LocationPickerDialog : DialogFragment() {

    private var _binding: DialogLocationPickerBinding? = null
    private val binding get() = _binding!!
    
    private var initialLat: Double = 46.5547
    private var initialLng: Double = 15.6467
    private var selectedLat: Double = 46.5547
    private var selectedLng: Double = 15.6467
    private var mapView: MapView? = null
    private var marker: Marker? = null
    
    var onLocationSelected: ((lat: Double, lng: Double) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLocationPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
        
        arguments?.let {
            initialLat = it.getDouble("lat", 46.5547)
            initialLng = it.getDouble("lng", 15.6467)
            selectedLat = initialLat
            selectedLng = initialLng
        }

        setupMapView()
        setupButtons()
        updateLocationDisplay()
    }

    private fun setupMapView() {
        val mapViewInstance = MapView(requireContext()).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(initialLat, initialLng))
        }
        
        mapView = mapViewInstance

        binding.mapContainer.removeAllViews()
        binding.mapContainer.addView(mapViewInstance)

        marker = Marker(mapViewInstance).apply {
            position = GeoPoint(initialLat, initialLng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Izbrana lokacija"
            isDraggable = true
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker?) {
                    marker?.let {
                        selectedLat = it.position.latitude
                        selectedLng = it.position.longitude
                        updateLocationDisplay()
                    }
                }
                
                override fun onMarkerDragEnd(marker: Marker?) {
                    marker?.let {
                        selectedLat = it.position.latitude
                        selectedLng = it.position.longitude
                        updateLocationDisplay()
                    }
                }
                
                override fun onMarkerDragStart(marker: Marker?) {}
            })
        }

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    selectedLat = it.latitude
                    selectedLng = it.longitude
                    marker?.position = it
                    updateLocationDisplay()
                }
                return true
            }
            
            override fun longPressHelper(p: GeoPoint?): Boolean {
                return false
            }
        }
        
        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapViewInstance.overlays.add(0, mapEventsOverlay)
        mapViewInstance.overlays.add(marker)
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            onLocationSelected?.invoke(selectedLat, selectedLng)
            dismiss()
        }

        binding.btnManualInput.setOnClickListener {
            val latText = binding.etLatitude.text.toString()
            val lngText = binding.etLongitude.text.toString()
            
            val lat = latText.toDoubleOrNull()
            val lng = lngText.toDoubleOrNull()
            
            if (lat != null && lng != null) {
                selectedLat = lat
                selectedLng = lng
                marker?.position = GeoPoint(lat, lng)
                mapView?.controller?.setCenter(GeoPoint(lat, lng))
                updateLocationDisplay()
            }
        }
    }

    private fun updateLocationDisplay() {
        binding.etLatitude.setText(String.format("%.6f", selectedLat))
        binding.etLongitude.setText(String.format("%.6f", selectedLng))
        binding.tvSelectedLocation.text = "Izbrano: ${String.format("%.6f", selectedLat)}, ${String.format("%.6f", selectedLng)}"
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDetach()
        _binding = null
    }

    companion object {
        fun newInstance(lat: Double, lng: Double): LocationPickerDialog {
            return LocationPickerDialog().apply {
                arguments = Bundle().apply {
                    putDouble("lat", lat)
                    putDouble("lng", lng)
                }
            }
        }
    }
}
