package com.example.lokerlokal.ui.map

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.lokerlokal.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapFragment : Fragment(R.layout.fragment_map) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            val jakarta = LatLng(-6.2088, 106.8456)
            googleMap.addMarker(MarkerOptions().position(jakarta).title("Jakarta"))
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(jakarta, 11f))
        }
    }
}

