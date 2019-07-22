package curso.carlos.indrive

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.crashlytics.android.Crashlytics
import com.google.android.gms.maps.SupportMapFragment
import com.google.firebase.auth.FirebaseAuth
import curso.carlos.indrive.gateway.LoginActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.*
import curso.carlos.indrive.helpers.Analytics
import curso.carlos.indrive.helpers.DistanceManager
import curso.carlos.indrive.repositories.RoutesRepository
import curso.carlos.indrive.services.MapService
import curso.carlos.indrive.services.RouteService
import curso.carlos.indrive.services.WeatherService
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity(), PlaceSelectionListener {
    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: InDriveLocationListener
    private lateinit var database: DatabaseReference
    private val wheaterService = WeatherService()
    private lateinit var mapService: MapService
    private lateinit var originLocation: Location
    private lateinit var destLocation: Place
    private lateinit var analytics: Analytics
    private val routesRepository = RoutesRepository()
    private lateinit var moreInfoBtn: Button

    private lateinit var getWeatherMetrics: Disposable
    private lateinit var getLocationOnMapReady: Disposable
    private lateinit var getLocationOnDriverRoutePainted: Disposable
    private lateinit var getDriverLocationUpdates: Disposable

    private val REQUEST_ACCESS_PERMISSION = 1
    private val CRASH_MAIN = "MainActivity"

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

        moreInfoBtn = findViewById(R.id.historyBtn)
        moreInfoBtn.setOnClickListener { onServicePickedUp() }

        getUserLocation()

        // Initialize Places.
        Places.initialize(applicationContext, "AIzaSyAF7ycjJe_F8sfUiDZVd_8tpMp2C72Mvrs")

        mapService = MapService(this)
        initMap()


        // Firebase reference
        database = FirebaseDatabase.getInstance().reference

        try{
            val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
            mapFragment!!.getMapAsync(mapService.onReadyMapCallback())
        } catch (ex: Exception) {
            Crashlytics.log(Log.DEBUG, CRASH_MAIN, "sucedió un problema cargando el mapa");
        }

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

        analytics = Analytics(this)

    }

    override fun onDestroy() {
        super.onDestroy()
        if (!getWeatherMetrics.isDisposed)
            getWeatherMetrics.dispose()
        if (!getLocationOnMapReady.isDisposed)
            getLocationOnMapReady.dispose()
        if (!getLocationOnDriverRoutePainted.isDisposed)
            getLocationOnDriverRoutePainted.dispose()
        if (!getDriverLocationUpdates.isDisposed)
            getDriverLocationUpdates.dispose()
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

    private fun connectPolyline(polylines: List<LatLng>) {
        mapService.paintPolyline(polylines)
        listenMyService(auth.currentUser!!.uid)
    }

    /**
     * redirigir a login
     */
    private fun redirectToLogin() {
        val intent = Intent(applicationContext, LoginActivity::class.java)
        startActivity(intent)
    }

    @SuppressLint("InflateParams")
    private fun createMountDialog(place: Place): Dialog {
        val dialogBuilder = AlertDialog.Builder(this)
        val inflater = this.layoutInflater
        val viewInflated = inflater.inflate(R.layout.dialog_service_mount,null)
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
        getLocationOnDriverRoutePainted =
            locationListener.locationUpdates.take(1).subscribe { location ->
                val routeService = RouteService()
                originLocation = location
                destLocation = place
                routeService.saveRoute(
                    auth.currentUser!!.uid,
                    auth.currentUser!!.email!!,
                    serviceMount,
                    location,
                    place
                )
                paintRoute(
                    location.latitude.toString(),
                    location.longitude.toString(),
                    place.latLng?.latitude.toString(),
                    place.latLng?.longitude.toString()
                )

                analytics.reportRouteCreated()
            }
    }

    private fun paintRoute(originLat: String, originLon: String, destLat: String, destLon: String) {
        val subs =
            mapService.getDirection(
                originLat,
                originLon,
                destLat,
                destLon
            )
                .doOnError {
                    Toast.makeText(
                        applicationContext,
                        "Failed Load Direction",
                        Toast.LENGTH_LONG
                    ).show()

                    Crashlytics.log(Log.DEBUG, CRASH_MAIN, "sucedió un problema consumiendo directions");
                }
                .subscribe { polyline ->
                    connectPolyline(polyline)
                }
    }

    private fun listenMyService(userId: String) {

        getDriverLocationUpdates = routesRepository.get(userId).subscribe { myRoute ->
            if (myRoute.status) {
                if (moreInfoBtn.visibility == View.INVISIBLE) {
                    moreInfoBtn.visibility = View.VISIBLE
                }

                routesRepository.getDriver(myRoute.drivername).subscribe { driverAssigned ->
                    if (driverAssigned != null) {
                        paintIcon(
                            driverAssigned.name,
                            driverAssigned.origin_lat.toDouble(),
                            driverAssigned.origin_long.toDouble()
                        )

                        val getLocationOnDriverRoutePainted =
                            locationListener.locationUpdates.take(1).subscribe { userLocation ->
                                val distance = DistanceManager.calculateDistance(driverAssigned.origin_lat.toDouble(),
                                    driverAssigned.origin_long.toDouble(),
                                    userLocation.latitude,
                                    userLocation.longitude)

                                notifyWhenDriverIsArriving(distance)

                                if(distance <= DistanceManager.LOWER_LIMIT_DISTANCE_METERS) {
                                    analytics.driverArrived()
                                }
                            }
                    }
                }
            } else {
                mapService.clearMap()

                val sub = mapService.getDirection(
                    originLocation.latitude.toString(),
                    originLocation.longitude.toString(),
                    destLocation.latLng?.latitude.toString(),
                    destLocation.latLng?.longitude.toString()
                ).subscribe { polyline ->
                    connectPolyline(polyline)
                }

                moreInfoBtn.visibility = View.INVISIBLE
            }
        }
    }

    private fun paintIcon(driver: String, lat: Double, lon: Double) {
        mapService.clearMap()

        val sub = mapService.getDirection(
            originLocation.latitude.toString(),
            originLocation.longitude.toString(),
            destLocation.latLng?.latitude.toString(),
            destLocation.latLng?.longitude.toString()
        ).doOnError {
            Crashlytics.log(Log.DEBUG, CRASH_MAIN, "no se pudo obtener la polyline")
        }
         .subscribe { polyline ->
            mapService.paintPolyline(polyline)
        }


        try{
            mapService.paintDriverIcon(driver, lat, lon)
        } catch (ex: Exception) {
            Crashlytics.log(Log.DEBUG, CRASH_MAIN, "no se pudo pintar la posición del conductor")
        }
    }


    private fun getTemperatureOnLocation() {
        getWeatherMetrics = locationListener.locationUpdates.take(1).subscribe { location ->
            wheaterService.getWeatherMetricsInLocation(
                location.latitude.toString(),
                location.longitude.toString()
            ).subscribe { weather ->
                tv_temperature.text = "Temperature is: ${weather.metrics.temp}"
            }
        }
    }


    private fun notifyWhenDriverIsArriving(distance: Float) {
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

    private fun listenLightSensor() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor != null) {
            sensorManager.registerListener(object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type == Sensor.TYPE_LIGHT) {

                        if (event.values[0] > 10000)
                            mapService.changeMapStyle(MapService.MAP_STYLE_TYPE.NIGHT_MODE)
                        else
                            mapService.changeMapStyle(MapService.MAP_STYLE_TYPE.DAY_MODE)
                    }
                }

            }, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

    }

    @SuppressLint("MissingPermission")
    private fun initMap() {
        val mapSubs = mapService.mapListener.subscribe { googleMap ->

            getLocationOnMapReady = locationListener.locationUpdates.take(1).subscribe { location ->
                mapService.reloadMapPosition(location.latitude, location.longitude)
            }

            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastLocation != null) {
                mapService.reloadMapPosition(lastLocation.latitude, lastLocation.longitude)
            }

            val currentTime = Calendar.getInstance()
            val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
            if (currentHour > 18 || (currentHour > 0 && currentHour <= 6))
                googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map))

            listenLightSensor()
        }
    }

    fun onServicePickedUp() {
        routesRepository.getHistory(auth.currentUser?.uid!!).subscribe { history ->
            val intent = Intent(this, HistoryActivity::class.java)
            intent.putExtra("history_carefare", history.carfare)
            intent.putExtra("history_destination_lat", history.destination_lat)
            intent.putExtra("history_destination_lon", history.destination_lon)
            intent.putExtra("history_origin_lat", history.origin_lat)
            intent.putExtra("history_origin_lon", history.origin_lon)
            intent.putExtra("history_service_id", history.service_id)

            startActivity(intent)
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
                    requestUserLocation()
                }

                return
            }
        }
    }

    override fun onPlaceSelected(p0: Place) {
        Toast.makeText(applicationContext, "" + p0.name + p0.latLng, Toast.LENGTH_LONG).show()

        createMountDialog(p0).show()
    }

    override fun onError(status: Status) {
        Toast.makeText(applicationContext, "" + status.toString(), Toast.LENGTH_LONG).show()

            Crashlytics.log(Log.DEBUG, CRASH_MAIN, "sucedió un problema cargando el listado de places");
    }

    inner class InDriveLocationListener() : LocationListener {
        var locationUpdates: BehaviorSubject<Location> = BehaviorSubject.create()

        override fun onLocationChanged(location: Location?) {
            locationUpdates.onNext(location!!)
        }

        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

        override fun onProviderEnabled(p0: String?) {}

        override fun onProviderDisabled(p0: String?) {}

    }


}