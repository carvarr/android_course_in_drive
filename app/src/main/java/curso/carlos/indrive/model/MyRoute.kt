package curso.carlos.indrive.model

class MapRoute {
    var drivername: String = "nil"
    var destination_lat: String = ""
    var destination_long: String = ""
    var origin_lat: String = ""
    var origin_long: String = ""
    var status: Boolean = false
    var username: String = ""
    lateinit var service_demand: MountDemand
}

class MountDemand {
    var drivername = "nil"
    var service_mount = 0

    constructor(mount: Int) {
        service_mount = mount
    }
}

class MyRoute {
    var drivername: String = "nil"
    var status: Boolean = false
}

class Driver {
    var name = ""
    var origin_lat = ""
    var origin_long = ""
}

class History {
    var carfare = 0
    var destination_lat = ""
    var destination_lon = ""
    var origin_lat = ""
    var origin_lon = ""
    var service_id = ""
}