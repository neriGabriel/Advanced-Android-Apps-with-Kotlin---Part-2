package com.udacity.project4.locationreminders.savereminder.selectreminderlocation


import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import java.util.*

class SelectLocationFragment : BaseFragment(), OnMapReadyCallback  {

    //Use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding

    private lateinit var lastLocation: Location
    private lateinit var map: GoogleMap
    private lateinit var selectedMarker: Marker

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_select_location, container, false)

        binding.viewModel = _viewModel
        binding.lifecycleOwner = this

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

        verifyLocationSettings()
        val fragmentMap = childFragmentManager.findFragmentById(R.id.select_map) as SupportMapFragment
        fragmentMap.getMapAsync(this)

        binding.saveButton.setOnClickListener {
            onLocationSelected()
        }

        return binding.root
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setStyleForMap()
        setLongClickForMap()
        setPOIClick()
        getDeviceLocation()
    }

    private fun setPOIClick() {
        map.setOnPoiClickListener  {
            map.clear()
            selectedMarker = map.addMarker(
                MarkerOptions()
                    .position(it.latLng)
                    .title(it.name)
            )
            selectedMarker.showInfoWindow()
        }
    }

    private fun setLongClickForMap() {
        map.setOnMapLongClickListener {
            map.clear()
            val snippet = String.format(
                Locale.getDefault(),
                "Latitude: %1$.5f, Longitude: %2$.5f",
                it.latitude,
                it.longitude
            )

            selectedMarker = map.addMarker(
                MarkerOptions()
                    .position(it)
                    .title("New PIN")
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )

            selectedMarker.showInfoWindow()
        }
    }

    private fun setStyleForMap() {
        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.map_style
                )
            )
            if (!success) {
                Log.e(TAG, "Style parsing failed.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Can't find style. Error: ", e)
        }
    }

    private fun onLocationSelected() {
        if(this::selectedMarker.isInitialized) {
            _viewModel.latitude.value = selectedMarker.position.latitude
            _viewModel.longitude.value = selectedMarker.position.longitude
            _viewModel.reminderSelectedLocationStr.value = selectedMarker.title
            findNavController().popBackStack()
        } else {
            Toast.makeText(requireContext(), "Please, select one location", Toast.LENGTH_SHORT)
                .show()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun getDeviceLocation() {
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(
            requireContext())

        try {
            if(isPermissionGranted()) {
                map.isMyLocationEnabled = true
                val locationResult = fusedLocationProviderClient.lastLocation
                locationResult.addOnCompleteListener {
                    if(it.isSuccessful && it.result != null) {
                        lastLocation = it.result!!
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                        lastLocation.latitude,
                                        lastLocation.longitude
                                ),
                                INITIAL_ZOOM
                        ))
                    } else {
                        Log.e(TAG, "Current location not found, using default one. " +
                                "Exception: ${it.exception}")
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(defaultLocation, INITIAL_ZOOM)
                        )
                        map.uiSettings.isMyLocationButtonEnabled = true
                    }
                }
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION
                )
            }
        } catch (exception: SecurityException) {
            Log.e(TAG, "Security exception while trying to get the device location: " +
                    "${exception.message}")
        }
    }

    private fun verifyLocationSettings(resolve: Boolean = true) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val locationSettingsRequestBuilder =
            LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val clientSettings = LocationServices.getSettingsClient(requireContext())
        val taskLocationSettingsResponse = clientSettings.checkLocationSettings(
            locationSettingsRequestBuilder.build())

        taskLocationSettingsResponse.addOnFailureListener { exception ->
            if(exception is ResolvableApiException && resolve) {
                /**
                 * Location settings was not truly satisfied for some specific reason (permissions,
                 * users or anything else), however we can prompt the permission dialog
                 * */
                try {
                    exception.startResolutionForResult(
                        this.requireActivity(),
                        REQUEST_TURN_DEVICE_LOCATION_ON
                    )
                } catch(intentSendException: IntentSender.SendIntentException) {
                    Log.d(TAG, "Error when tried to get location settings: " +
                            "${intentSendException.message}")
                }
            } else {
                Snackbar.make(this.requireView(), "Error while trying to get location permission",
                    Snackbar.LENGTH_INDEFINITE).setAction(android.R.string.ok) {
                        verifyLocationSettings()
                }.show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_LOCATION_PERMISSION && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getDeviceLocation()
        } else {
            Snackbar.make(this.requireView(), "Error while trying to get location permission",
                Snackbar.LENGTH_INDEFINITE).setAction(android.R.string.ok) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION
                )
            }.show()
        }
    }

    private fun isPermissionGranted() = ContextCompat.checkSelfPermission(requireContext(),
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "SelectLocationFragment"
        private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val INITIAL_ZOOM = 17f
        private val defaultLocation = LatLng(-22.80646752746602, -47.309289937563987)
    }
}
