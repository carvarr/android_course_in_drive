package curso.carlos.indrive.services

import android.location.Location
import com.google.android.libraries.places.api.model.Place
import curso.carlos.indrive.model.MapRoute
import curso.carlos.indrive.model.MountDemand
import curso.carlos.indrive.repositories.RoutesRepository

class RouteService {

    private val repository = RoutesRepository()

    /**
     * save a route in firebase
     * @param userId user id
     * @param username username
     * @param serviceMount service cost
     * @param originLocation user origin location
     * @param destLocation user destination location
     *
     * @return Void
     */
    fun saveRoute(userId: String, username: String, serviceMount: Int, originLocation: Location, destLocation: Place) {
        val route = MapRoute()
        route.origin_lat = originLocation.latitude.toString()
        route.origin_long = originLocation.longitude.toString()
        route.destination_lat = destLocation.latLng!!.latitude.toString()
        route.destination_long = destLocation.latLng!!.longitude.toString()
        route.username = userId
        route.service_demand = MountDemand(serviceMount)

        repository.save(userId, route)
    }


}