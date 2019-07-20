package curso.carlos.indrive.helpers

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class Analytics {

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    constructor(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    /**
     * reporta cuando se crea una ruta
     */
    fun reportRouteCreated() {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "Route")
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Route created")
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
    }

    /**
     * reporta cuando el conductor llega al origen
     */
    fun driverArrived() {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "Route")
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Driver arrived")
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
    }
}