package curso.carlos.indrive.services

import android.content.Context
import android.support.design.widget.AppBarLayout
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import curso.carlos.indrive.R
import curso.carlos.indrive.helpers.CacheManager
import curso.carlos.indrive.helpers.ImageManager
import curso.carlos.indrive.services.dto.Direction
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.collections.ArrayList

class MapService {

    private val retrofit: Retrofit
    private val cache: CacheManager
    private lateinit var map: GoogleMap
    private lateinit var context: Context

    val mapListener = BehaviorSubject.create<GoogleMap>()

    constructor(context: Context) {
        this.context = context
        cache = CacheManager(context)

        retrofit = Retrofit.Builder()
            .baseUrl(GOOGLE_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun onReadyMapCallback() = OnMapReadyCallback {googleMap ->
        map = googleMap
        mapListener.onNext(map)
    }

    fun getDirection(originLat: String, originLon: String, destLat: String, destLon: String): Single<List<LatLng>> {

        return Single.create { emitter ->
            val polylineCached = cache.getValue("$originLat,$originLon,$destLat,$destLon")
            if (!polylineCached.isEmpty()) {
                emitter.onSuccess(decodePoly(polylineCached))
            }

            val service = retrofit.create(DirectionsService::class.java)
            val call = service.getDirection(
                "$originLat,$originLon",
                "$destLat, $destLon"
            )
            call.enqueue(object : Callback<Direction> {
                override fun onResponse(call: Call<Direction>, response: Response<Direction>) {
                    if (response.code() == 200) {
                        cache.setValue("$originLat,$originLon,$destLat,$destLon", response.body()!!.routes[0].polyline.points)
                        val polylineDecoded = decodePoly(response.body()!!.routes[0].polyline.points)
                        emitter.onSuccess(polylineDecoded)
                    }
                }

                override fun onFailure(call: Call<Direction>, t: Throwable) {
                    emitter.onError(t)
                }
            })

        }

    }

    fun clearMap() {
        map.clear()
    }

    fun changeMapStyle(mapStyle: MAP_STYLE_TYPE) = when(mapStyle) {
        MAP_STYLE_TYPE.DAY_MODE ->  map.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                context,
                R.raw.empty
            )
        )
        MAP_STYLE_TYPE.NIGHT_MODE -> map.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                context,
                R.raw.map
            )
        )
    }

    fun reloadMapPosition(latitude: Double?, longitude: Double?) {
        val lat: Double = latitude ?: 0.0
        val lon: Double = longitude ?: 0.0

        val pos = LatLng(lat, lon)
        map.addMarker(MarkerOptions().position(pos).title("My position"))
        map.moveCamera(CameraUpdateFactory.newLatLng(pos))
        map.setMinZoomPreference(5f)
    }


    fun paintPolyline(polylines: List<LatLng>) {
        val polylineOptions = PolylineOptions().addAll(polylines).clickable(true)
        map.addPolyline(polylineOptions)
    }

    fun paintDriverIcon(driverName: String, lat: Double, lon: Double) {
        val markerOptions = MarkerOptions()
            .position(LatLng(lat, lon))
            .title(driverName)
            .icon(ImageManager.bitmapDescriptorFromVector(context, R.drawable.drive_indicator))

        map.addMarker(markerOptions)
    }

    enum class MAP_STYLE_TYPE {
        DAY_MODE,
        NIGHT_MODE
    }

    companion object {

        private const val GOOGLE_BASE_URL = "https://maps.googleapis.com/"

        fun decodePoly(encoded: String): List<LatLng> {
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

    }
}