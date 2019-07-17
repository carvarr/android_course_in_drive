package curso.carlos.indrive.services

import android.widget.Toast
import curso.carlos.indrive.repositories.WeatherRepository
import curso.carlos.indrive.services.dto.Direction
import curso.carlos.indrive.services.dto.Weather
import io.reactivex.Observable
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherService {

    private lateinit var retrofit: Retrofit

    companion object {
        const val WEATHER_URL = "http://api.openweathermap.org/data/2.5/"
    }

    constructor(){
        retrofit = Retrofit.Builder()
            .baseUrl(WEATHER_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }


    fun getWeatherMetricsInLocation(lat: String, lon: String): Observable<Weather> {
        return Observable.create { emitter ->

            val service = retrofit.create(WeatherRepository::class.java)
            val call = service.getWeather(lat, lon)
            call.enqueue(object: Callback<Weather> {
                override fun onResponse(call: Call<Weather>, response: Response<Weather>) {
                    if(response.code() == 200){
                        emitter.onNext(response.body()!!)
                    }
                }

                override fun onFailure(call: Call<Weather>, t: Throwable) {
                    emitter.onError(t)
                }
            })

        }
    }
}