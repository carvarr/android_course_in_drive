package curso.carlos.indrive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.firebase.auth.FirebaseAuth
import curso.carlos.indrive.gateway.LoginActivity
/*import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.LatLng*/
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import curso.carlos.indrive.services.DirectionsService
import curso.carlos.indrive.services.dto.Direction
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.log


class MainActivity : AppCompatActivity(), OnMapReadyCallback, PlaceSelectionListener {
    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: InDriveLocationListener
    private lateinit var googleMap: GoogleMap
    private lateinit var database: DatabaseReference

    private val REQUEST_ACCESS_PERMISSION = 1
    private val GOOGLE_BASE_URL = "https://maps.googleapis.com/"

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

        val logoutBtn = findViewById<Button>(R.id.logoutBtn)
        logoutBtn.setOnClickListener {
            val intent = Intent(applicationContext, LoginActivity::class.java)
            startActivity(intent)
        }

        // Initialize Places.
        Places.initialize(applicationContext, "AIzaSyAF7ycjJe_F8sfUiDZVd_8tpMp2C72Mvrs")

        // Firebase reference
        database = FirebaseDatabase.getInstance().reference

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)

        // Initialize the AutocompleteSupportFragment.
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment?
        autocompleteFragment!!.setPlaceFields(arrayListOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
        autocompleteFragment.setOnPlaceSelectedListener(this)

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
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0.1f, locationListener)
    }

    private fun reloadMapPosition(latitude: Double?, longitude: Double?) {
        val lat: Double = latitude ?: 0.0
        val lon: Double = longitude ?: 0.0

        val pos = LatLng(lat,lon)
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

            val p = LatLng(lat.toDouble() / 1E5,
                lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }

    private fun connectPolyline(polylines: List<LatLng>) {
        val polylineOptions = PolylineOptions().addAll(polylines).clickable(true)
        googleMap.addPolyline(polylineOptions)
    }

    /**
     * redirigir a login
     */
    private fun redirectToLogin() {
        val intent = Intent(applicationContext, LoginActivity::class.java)
        startActivity(intent)
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


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
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
        reloadMapPosition(locationListener.mylocation?.latitude, locationListener.mylocation?.longitude)
    }

    override fun onPlaceSelected(p0: Place) {
        Toast.makeText(applicationContext,""+p0!!.name+p0!!.latLng,Toast.LENGTH_LONG).show()

        val route = MapRoute()
        route.origin_lat = locationListener.mylocation?.latitude.toString()
        route.origin_long = locationListener.mylocation?.longitude.toString()
        route.destination_lat = p0.latLng!!.latitude.toString()
        route.destination_long = p0.latLng!!.longitude.toString()

        saveUserRoute(auth.currentUser!!.uid, route)

        val retrofit = Retrofit.Builder()
            .baseUrl(GOOGLE_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(DirectionsService::class.java)
        val call = service.getDirection("${route.origin_lat},${route.origin_long}", "${route.destination_lat}, ${route.destination_long}")
        call.enqueue(object: Callback<Direction> {
            override fun onResponse(call: Call<Direction>, response: Response<Direction>) {
                if(response.code() == 200){
                    val polylineDecoded = decodePoly(response.body()!!.routes[0].polyline.points)
                    connectPolyline(polylineDecoded)
                }
            }

            override fun onFailure(call: Call<Direction>, t: Throwable) {
                Toast.makeText(applicationContext,"Failed Load Direction",Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onError(status: Status) {
        Toast.makeText(applicationContext,""+status.toString(),Toast.LENGTH_LONG).show()
    }

    inner class InDriveLocationListener : LocationListener {
        var mylocation: Location?

        constructor() : super() {
            mylocation = Location("me")
            mylocation!!.longitude
            mylocation!!.latitude
        }

        override fun onLocationChanged(location: Location?) {
            mylocation = location
        }

        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

        override fun onProviderEnabled(p0: String?) {}

        override fun onProviderDisabled(p0: String?) {}
    }

    inner class MapRoute {
        var destination_lat: String = ""
        var destination_long: String = ""
        var origin_lat: String = ""
        var origin_long: String = ""
    }

}