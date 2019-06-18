package curso.carlos.indrive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.widget.Button
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.firebase.auth.FirebaseAuth
import curso.carlos.indrive.gateway.LoginActivity
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient


class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: InDriveLocationListener
    private lateinit var googleMap: GoogleMap
    private lateinit var placesClient: PlacesClient

    private val REQUEST_ACCESS_PERMISSION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        auth = FirebaseAuth.getInstance()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val currentUser = auth.currentUser
        if (currentUser == null) {
            val intent = Intent(applicationContext, LoginActivity::class.java)
            startActivity(intent)
        }

        getUserLocation()

        val logoutBtn = findViewById<Button>(R.id.logoutBtn)
        logoutBtn.setOnClickListener {
            val intent = Intent(applicationContext, LoginActivity::class.java)
            startActivity(intent)
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)

        // Initialize Places.
        Places.initialize(applicationContext, "apiKey")
        placesClient = Places.createClient(this)

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_ACCESS_PERMISSION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    requestUserLocation()
                }

                return
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        reloadMapPosition(locationListener.mylocation?.latitude, locationListener.mylocation?.longitude)
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

}