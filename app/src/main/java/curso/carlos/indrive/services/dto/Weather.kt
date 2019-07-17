package curso.carlos.indrive.services.dto

import com.google.gson.annotations.SerializedName

class Weather {
    @SerializedName("main")
    var metrics = Metrics()
}

class Metrics {
    var temp = 0.0
}