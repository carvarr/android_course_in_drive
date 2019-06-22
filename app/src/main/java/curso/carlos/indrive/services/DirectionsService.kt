package curso.carlos.indrive.services

import curso.carlos.indrive.services.dto.Direction
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsService {

    @GET("maps/api/directions/json?key=AIzaSyAF7ycjJe_F8sfUiDZVd_8tpMp2C72Mvrs")
    fun getDirection(@Query("origin") origin: String, @Query("destination") destination: String): Call<Direction>

}