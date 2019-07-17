package curso.carlos.indrive.repositories

import curso.carlos.indrive.services.dto.Weather
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherRepository {

    @GET("weather?units=metric&appid=fbb1d1774636cc5f9698a004b89a6022")
    fun getWeather(@Query("lat") lat: String, @Query("lon") lon: String): Call<Weather>

}