package curso.carlos.indrive.repositories

import com.google.firebase.database.*
import curso.carlos.indrive.model.Driver
import curso.carlos.indrive.model.History
import curso.carlos.indrive.model.MapRoute
import curso.carlos.indrive.model.MyRoute
import io.reactivex.Observable
import io.reactivex.Single
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

class RoutesRepository {

    private val db = FirebaseDatabase.getInstance().reference

    /**
     * save route in firebase
     */
    fun save(userId: String, route: MapRoute) {
        db.child("addresess").child("address_$userId").setValue(route)
    }


    /**
     * get a route given an user id
     */
    fun get(userId: String): Observable<MyRoute> {
        return Observable.create { emitter ->
            db.child("addresess").child("address_$userId")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val myRoute = snapshot.getValue(MyRoute::class.java)
                        emitter.onNext(myRoute!!)
                    }

                    override fun onCancelled(p0: DatabaseError) {
                        emitter.onError(p0.toException())
                    }
                })
        }
    }

    /**
     * get driver given a driver name
     */
    fun getDriver(driverName: String): Observable<Driver> {
        return Observable.create { emitter ->
            db.child("drivers").child("driver_$driverName")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(dsnapshot: DataSnapshot) {
                        val driverAssigned = dsnapshot.getValue(Driver::class.java)
                        emitter.onNext(driverAssigned!!)
                    }

                    override fun onCancelled(p0: DatabaseError) {
                        emitter.onError(p0.toException())
                    }
                })
        }
    }

    fun getHistory(userId: String): Observable<History> {
        return Observable.create { emitter ->
            db.child("service_history").child(userId).addValueEventListener(object: ValueEventListener {
                override fun onDataChange(dsnapshot: DataSnapshot) {
                    val history = dsnapshot.getValue(History::class.java)
                    emitter.onNext(history!!)
                }

                override fun onCancelled(p0: DatabaseError) {
                    emitter.onError(p0.toException())
                }
            })
        }
    }

}