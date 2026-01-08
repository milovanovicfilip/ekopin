package com.example.mobile.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.mobile.R
import com.example.mobile.map.MapGdxApp

class MapFragment : AndroidFragmentApplication() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_map, container, false)

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = false
        }

        val gdxView = initializeForView(MapGdxApp(), config)

        root.findViewById<ViewGroup>(R.id.gdx_container).addView(
            gdxView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        return root
    }


    override fun exit() {
    }
}