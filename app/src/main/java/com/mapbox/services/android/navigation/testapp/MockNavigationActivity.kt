package com.mapbox.services.android.navigation.testapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.mapbox.api.directions.v5.DirectionsCriteria.METRIC
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.services.android.navigation.testapp.databinding.ActivityMockNavigationBinding
import com.mapbox.services.android.navigation.ui.v5.route.NavigationRoute
import com.mapbox.services.android.navigation.v5.instruction.Instruction
import com.mapbox.services.android.navigation.v5.location.replay.ReplayRouteLocationEngine
import com.mapbox.services.android.navigation.v5.milestone.*
import com.mapbox.services.android.navigation.v5.models.DirectionsRoute
import com.mapbox.services.android.navigation.v5.navigation.*
import com.mapbox.services.android.navigation.v5.offroute.OffRouteListener
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import okhttp3.Request
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import java.lang.ref.WeakReference

class MockNavigationActivity :
    AppCompatActivity(),
    OnMapReadyCallback,
    MapboxMap.OnMapClickListener,
    ProgressChangeListener,
    NavigationEventListener,
    MilestoneEventListener,
    OffRouteListener {
    private val BEGIN_ROUTE_MILESTONE = 1001
    private lateinit var mapboxMap: MapboxMap

    // Navigation related variables
    private var locationEngine: ReplayRouteLocationEngine = ReplayRouteLocationEngine()
    private lateinit var navigation: MapboxNavigation
    private var route: DirectionsRoute? = null
    private var navigationMapRoute: NavigationMapRoute? = null
    private var destination: Point? = null
    private var waypoint: Point? = null
    private var locationComponent: LocationComponent? = null

    private lateinit var binding: ActivityMockNavigationBinding

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMockNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mapView.apply {
            onCreate(savedInstanceState)
            getMapAsync(this@MockNavigationActivity)
        }

        val context = applicationContext
        val customNotification = CustomNavigationNotification(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            customNotification.createNotificationChannel(this)
        }
        val options = MapboxNavigationOptions.builder()
            .navigationNotification(customNotification)
            .build()

        navigation = MapboxNavigation(this, options)

        navigation.addMilestone(
            RouteMilestone.Builder()
                .setIdentifier(BEGIN_ROUTE_MILESTONE)
                .setInstruction(BeginRouteInstruction())
                .setTrigger(
                    Trigger.all(
                        Trigger.lt(TriggerProperty.STEP_INDEX, 3),
                        Trigger.gt(TriggerProperty.STEP_DISTANCE_TOTAL_METERS, 200),
                        Trigger.gte(TriggerProperty.STEP_DISTANCE_TRAVELED_METERS, 75),
                    ),
                ).build(),
        )
        customNotification.register(MyBroadcastReceiver(navigation), context)

        binding.startRouteButton.setOnClickListener {
            route?.let { route ->
                binding.startRouteButton.visibility = View.INVISIBLE

                // Attach all of our navigation listeners.
                navigation.apply {
                    addNavigationEventListener(this@MockNavigationActivity)
                    addProgressChangeListener(this@MockNavigationActivity)
                    addMilestoneEventListener(this@MockNavigationActivity)
                    addOffRouteListener(this@MockNavigationActivity)
                }

                locationEngine.also {
                    it.assign(route)
                    navigation.locationEngine = it
                    navigation.startNavigation(route)
                    if (::mapboxMap.isInitialized) {
                        mapboxMap.removeOnMapClickListener(this)
                    }
                }
            }
        }

        binding.newLocationFab.setOnClickListener {
            newOrigin()
        }

        binding.clearPoints.setOnClickListener {
            if (::mapboxMap.isInitialized) {
                mapboxMap.markers.forEach {
                    mapboxMap.removeMarker(it)
                }
            }
            destination = null
            waypoint = null
            it.visibility = View.GONE

            navigationMapRoute?.removeRoute()
        }
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.Builder().fromUri(getString(R.string.map_style_light))) { style ->
            enableLocationComponent(style)
        }

        navigationMapRoute = NavigationMapRoute(navigation, binding.mapView, mapboxMap)

        mapboxMap.addOnMapClickListener(this)
        Snackbar.make(
            findViewById(R.id.container),
            "Tap map to place waypoint",
            Snackbar.LENGTH_LONG,
        ).show()

        newOrigin()
    }

    @SuppressWarnings("MissingPermission")
    private fun enableLocationComponent(style: Style) {
        // Get an instance of the component
        locationComponent = mapboxMap.locationComponent

        locationComponent?.let {
            // Activate with a built LocationComponentActivationOptions object
            it.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, style).build(),
            )

            // Enable to make component visible
            it.isLocationComponentEnabled = true

            // Set the component's camera mode
            it.cameraMode = CameraMode.TRACKING

            // Set the component's render mode
            it.renderMode = RenderMode.GPS

            it.locationEngine = locationEngine
        }
    }

    override fun onMapClick(point: LatLng): Boolean {
        var addMarker = true
        when {
            destination == null -> destination = Point.fromLngLat(point.longitude, point.latitude)
            waypoint == null -> waypoint = Point.fromLngLat(point.longitude, point.latitude)
            else -> {
                Toast.makeText(this, "Only 2 waypoints supported", Toast.LENGTH_LONG).show()
                addMarker = false
            }
        }

        if (addMarker) {
            mapboxMap.addMarker(MarkerOptions().position(point))
        }
        binding.clearPoints.visibility = View.VISIBLE

        binding.startRouteButton.visibility = View.VISIBLE
        calculateRoute()
        return true
    }

    private fun calculateRoute() {
        val userLocation = locationEngine.lastLocation
        val destination = destination
        if (userLocation == null) {
            Timber.d("calculateRoute: User location is null, therefore, origin can't be set.")
            return
        }

        if (destination == null) {
            return
        }

        val origin = Point.fromLngLat(userLocation.longitude, userLocation.latitude)
        if (TurfMeasurement.distance(origin, destination, TurfConstants.UNIT_METERS) < 50) {
            binding.startRouteButton.visibility = View.GONE
            return
        }

        val navigationRouteBuilder = NavigationRoute.builder(this).apply {
            this.accessToken(getString(R.string.mapbox_access_token))
            this.origin(origin)
            this.destination(destination)
            this.voiceUnits(METRIC)
            this.alternatives(true)
            this.user("gh")
            this.profile("car")
            this.baseUrl(getString(R.string.base_url))
        }

        navigationRouteBuilder.build().getRoute(object : Callback<DirectionsResponse> {
            override fun onResponse(
                call: Call<DirectionsResponse>,
                response: Response<DirectionsResponse>,
            ) {
                Timber.d("Url: %s", (call.request() as Request).url.toString())
                response.body()?.let { response ->
                    if (response.routes().isNotEmpty()) {
                        val maplibreResponse = com.mapbox.services.android.navigation.v5.models.DirectionsResponse.fromJson(response.toJson());
                        val directionsRoute = DirectionsRoute.fromJson(maplibreResponse.routes().first().toJson())
                        this@MockNavigationActivity.route = directionsRoute
                        navigationMapRoute?.addRoutes(maplibreResponse.routes())
                    }
                }
            }

            override fun onFailure(call: Call<DirectionsResponse>, throwable: Throwable) {
                Timber.e(throwable, "onFailure: navigation.getRoute()")
            }
        })
    }

    override fun onProgressChange(location: Location?, routeProgress: RouteProgress?) {
    }

    override fun onRunning(running: Boolean) {
    }

    override fun onMilestoneEvent(
        routeProgress: RouteProgress?,
        instruction: String?,
        milestone: Milestone?,
    ) {
    }

    override fun userOffRoute(location: Location?) {
    }

    private class BeginRouteInstruction : Instruction() {

        override fun buildInstruction(routeProgress: RouteProgress): String {
            return "Have a safe trip!"
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        navigation.onDestroy()
        if (::mapboxMap.isInitialized) {
            mapboxMap.removeOnMapClickListener(this)
        }
        binding.mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    private class MyBroadcastReceiver internal constructor(navigation: MapboxNavigation) :
        BroadcastReceiver() {
        private val weakNavigation: WeakReference<MapboxNavigation> = WeakReference(navigation)

        override fun onReceive(context: Context, intent: Intent) {
            weakNavigation.get()?.stopNavigation()
        }
    }

    private fun newOrigin() {
        mapboxMap.let {
            val latLng = LatLng(52.039176, 5.550339)
            locationEngine.assignLastLocation(
                Point.fromLngLat(latLng.longitude, latLng.latitude),
            )
            it.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12.0))
        }
    }
}
