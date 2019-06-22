package curso.carlos.indrive.services.dto

import com.google.gson.annotations.SerializedName

class Direction {
    @SerializedName("routes")
    var routes = ArrayList<Route>()
}

class Route {
    @SerializedName("overview_polyline")
    var polyline = Polyline()
}

class Polyline {
    @SerializedName("points")
    var points: String = ""
}