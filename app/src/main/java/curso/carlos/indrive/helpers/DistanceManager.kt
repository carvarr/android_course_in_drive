package curso.carlos.indrive.helpers

import android.location.Location

class DistanceManager {

    companion object {

        const val UPPER_LIMIT_DISTANCE_METERS = 300

        /**
         * calculate distance between two points
         */
        fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lng1, lat2, lng2, results)
            return results[0]
        }

    }
}