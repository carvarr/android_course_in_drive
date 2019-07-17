package curso.carlos.indrive

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.firebase.auth.FirebaseAuth
import curso.carlos.indrive.gateway.LoginActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.database.*
import curso.carlos.indrive.helpers.CacheManager
import curso.carlos.indrive.helpers.DistanceManager
import curso.carlos.indrive.model.Driver
import curso.carlos.indrive.model.MyRoute
import curso.carlos.indrive.services.DirectionsService
import curso.carlos.indrive.services.WeatherService
import curso.carlos.indrive.services.dto.Direction
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PlaceSelectionListener {
    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: InDriveLocationListener
    private lateinit var googleMap: GoogleMap
    private lateinit var database: DatabaseReference
    private val wheaterService = WeatherService()

    private lateinit var getWeatherMetrics: Disposable
    private lateinit var getLocationOnMapReady: Disposable
    private lateinit var getLocationOnSaveRoute: Disposable
    private lateinit var getLocationOnDriverRoutePainted: Disposable

    private val REQUEST_ACCESS_PERMISSION = 1
    private val GOOGLE_BASE_URL = "https://maps.googleapis.com/"
    private val POLYLINE_CACHE_KEY = "polyline"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        auth = FirebaseAuth.getInstance()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val currentUser = auth.currentUser
        if (currentUser == null) {
            redirectToLogin()
            return
        }

        getUserLocation()

        // Initialize Places.
        Places.initialize(applicationContext, "AIzaSyAF7ycjJe_F8sfUiDZVd_8tpMp2C72Mvrs")

        // Firebase reference
        database = FirebaseDatabase.getInstance().reference

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)

        // Initialize the AutocompleteSupportFragment.
        val autocompleteFragment =
            supportFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment?
        autocompleteFragment!!.setPlaceFields(
            arrayListOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG
            )
        )
        autocompleteFragment.setOnPlaceSelectedListener(this)

        getTemperatureOnLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        getWeatherMetrics.dispose()
        getLocationOnMapReady.dispose()
        getLocationOnSaveRoute.dispose()
        getLocationOnDriverRoutePainted.dispose()
    }

    private fun getUserLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ), REQUEST_ACCESS_PERMISSION
            )
        } else {
            requestUserLocation()
        }
    }

    @SuppressLint("MissingPermission")
    fun requestUserLocation() {
        locationListener = InDriveLocationListener()
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0,
            0.1f,
            locationListener
        )
    }

    private fun reloadMapPosition(latitude: Double?, longitude: Double?) {
        val lat: Double = latitude ?: 0.0
        val lon: Double = longitude ?: 0.0

        val pos = LatLng(lat, lon)
        this.googleMap.addMarker(MarkerOptions().position(pos).title("My position"))
        this.googleMap.moveCamera(CameraUpdateFactory.newLatLng(pos))
        this.googleMap.setMinZoomPreference(5f)
    }

    private fun saveUserRoute(userId: String, route: MapRoute) {
        database.child("addresess").child("address_$userId").setValue(route)
    }

    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }

        return poly
    }

    private fun connectPolyline(polylines: List<LatLng>) {
        val polylineOptions = PolylineOptions().addAll(polylines).clickable(true)
        googleMap.addPolyline(polylineOptions)
        listenMyService(auth.currentUser!!.uid)
    }

    /**
     * redirigir a login
     */
    private fun redirectToLogin() {
        val intent = Intent(applicationContext, LoginActivity::class.java)
        startActivity(intent)
    }

    private fun createMountDialog(place: Place): Dialog {
        val dialogBuilder = AlertDialog.Builder(this)
        val inflater = this.layoutInflater
        val viewInflated = inflater.inflate(R.layout.dialog_service_mount, null)
        val mountText = viewInflated.findViewById<EditText>(R.id.mount)

        dialogBuilder.setView(viewInflated)
            .setPositiveButton(R.string.save) { dialog, _ ->
                if (mountText.text.trim().isNotEmpty()) {
                    saveRoute(place, mountText.text.toString().toInt())
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }

        return dialogBuilder.create()
    }


    private fun saveRoute(place: Place, serviceMount: Int) {
        val route = MapRoute()

        getLocationOnSaveRoute = locationListener.locationUpdates.subscribe { location ->
            route.origin_lat = location.latitude.toString()
            route.origin_long = location.longitude.toString()
            route.destination_lat = place.latLng!!.latitude.toString()
            route.destination_long = place.latLng!!.longitude.toString()
            route.username = auth.currentUser!!.email!!
            route.service_demand = MountDemand(serviceMount)

            saveUserRoute(auth.currentUser!!.uid, route)
            paintRoute(route)
        }
    }

    private fun paintRoute(route: MapRoute) {

        if (paintPolylineFromCache()) {
            return
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(GOOGLE_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(DirectionsService::class.java)
        val call = service.getDirection(
            "${route.origin_lat},${route.origin_long}",
            "${route.destination_lat}, ${route.destination_long}"
        )
        call.enqueue(object : Callback<Direction> {
            override fun onResponse(call: Call<Direction>, response: Response<Direction>) {
                if (response.code() == 200) {
                    val polylineDecoded = decodePoly(response.body()!!.routes[0].polyline.points)
                    savePolylineInCache(response.body()!!.routes[0].polyline.points)

                    connectPolyline(polylineDecoded)
                }
            }

            override fun onFailure(call: Call<Direction>, t: Throwable) {
                Toast.makeText(applicationContext, "Failed Load Direction", Toast.LENGTH_LONG)
                    .show()
            }
        })
    }

    private fun listenMyService(userId: String) {
        database.child("addresess").child("address_$userId")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val myRoute = snapshot.getValue(MyRoute::class.java)

                    if (myRoute!!.status) {

                        database.child("drivers").child("driver_${myRoute.drivername}")
                            .addValueEventListener(object : ValueEventListener {
                                override fun onCancelled(p0: DatabaseError) {}

                                override fun onDataChange(dsnapshot: DataSnapshot) {
                                    val driverAssigned = dsnapshot.getValue(Driver::class.java)

                                    if (driverAssigned != null) {
                                        paintIcon(
                                            driverAssigned!!.name,
                                            driverAssigned.origin_lat.toDouble(),
                                            driverAssigned.origin_long.toDouble()
                                        )

                                        getLocationOnDriverRoutePainted =
                                            locationListener.locationUpdates.subscribe { userLocation ->
                                                notifyWhenDriverIsArriving(
                                                    driverAssigned.origin_lat.toDouble(),
                                                    driverAssigned.origin_long.toDouble(),
                                                    userLocation.latitude,
                                                    userLocation.longitude
                                                )
                                            }
                                    }
                                }

                            })
                    } else {
                        clearIcon()
                    }
                }

                override fun onCancelled(p0: DatabaseError) {}
            })
    }

    private fun paintIcon(driver: String, lat: Double, lon: Double) {
        clearIcon()
        paintPolylineFromCache()

        val markerOptions = MarkerOptions()
            .position(LatLng(lat, lon))
            .title(driver)
            .icon(bitmapDescriptorFromVector(this, R.drawable.drive_indicator))

        googleMap.addMarker(markerOptions)
    }

    private fun paintPolylineFromCache(): Boolean {
        val cache = CacheManager(this)
        val polylineCached = cache.getValue(POLYLINE_CACHE_KEY)
        if (!polylineCached.isEmpty()) {
            connectPolyline(decodePoly(polylineCached))
            return true
        }

        return false
    }

    private fun savePolylineInCache(polyline: String) {
        val cache = CacheManager(this)
        cache.setValue(POLYLINE_CACHE_KEY, polyline)
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)
        vectorDrawable!!.setBounds(0, 0, 100, 100)
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun clearIcon() {
        googleMap.clear()
    }

    private fun getTemperatureOnLocation() {
        getWeatherMetrics = locationListener.locationUpdates.subscribe { location ->
            wheaterService.getWeatherMetricsInLocation(
                location.latitude.toString(),
                location.longitude.toString()
            ).subscribe { weather ->
                tv_temperature.text = "Temperature is: ${weather.metrics.temp}"
            }
        }
    }


    fun notifyWhenDriverIsArriving(
        driverLat: Double,
        driverLon: Double,
        userLat: Double,
        userLong: Double
    ) {
        val distance = DistanceManager.calculateDistance(driverLat, driverLon, userLat, userLong)
        if (distance <= DistanceManager.UPPER_LIMIT_DISTANCE_METERS) {
            var builder = NotificationCompat.Builder(this, "")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Your driver is near")
                .setContentText("hey your driver is $distance meters from your location")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            with(NotificationManagerCompat.from(this)) {
                notify(0, builder.build())
            }

        }
    }

    // Menu configuration
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_history -> {
            true
        }
        R.id.action_profile -> {
            true
        }
        R.id.action_about -> {
            true
        }
        R.id.action_logout -> {
            auth.signOut()
            redirectToLogin()
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_ACCESS_PERMISSION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    //requestUserLocation()
                }

                return
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        getLocationOnMapReady = locationListener.locationUpdates.subscribe { location ->
            reloadMapPosition(location.latitude, location.longitude)
        }
    }

    override fun onPlaceSelected(p0: Place) {
        Toast.makeText(applicationContext, "" + p0!!.name + p0!!.latLng, Toast.LENGTH_LONG).show()

        createMountDialog(p0).show()
    }

    override fun onError(status: Status) {
        Toast.makeText(applicationContext, "" + status.toString(), Toast.LENGTH_LONG).show()
    }

    inner class InDriveLocationListener : LocationListener {
        var locationUpdates = BehaviorSubject.create<Location>()

        constructor() : super() {}

        override fun onLocationChanged(location: Location?) {
            locationUpdates.onNext(location!!)
        }

        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

        override fun onProviderEnabled(p0: String?) {}

        override fun onProviderDisabled(p0: String?) {}

    }

    inner class MapRoute {
        var drivername: String = "nil"
        var destination_lat: String = ""
        var destination_long: String = ""
        var origin_lat: String = ""
        var origin_long: String = ""
        var status: Boolean = false
        var username: String = ""
        lateinit var service_demand: MountDemand
    }

    inner class MountDemand {
        var drivername = "nil"
        var service_mount = 0

        constructor(mount: Int) {
            service_mount = mount
        }
    }


}