package com.gapiyka.kyivwalker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.cachemanager.CacheManager.CacheManagerCallback
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.round

class MapsActivity : AppCompatActivity(), LocationListener  {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private lateinit var map : MapView
    private lateinit var tLat : TextView
    private lateinit var tLong : TextView
    private lateinit var btFollowMe : ImageButton
    private lateinit var roadManager: RoadManager
    private lateinit var locationManager: LocationManager
    private lateinit var mapEventsOverlay: MapEventsOverlay
    private lateinit var rotationGestureOverlay: RotationGestureOverlay
    private lateinit var locationOverlay : MyLocationNewOverlay
    private lateinit var compassOverlay : CompassOverlay
    private lateinit var scaleBarOverlay : ScaleBarOverlay
    private lateinit var pointsOverlay : ItemizedOverlayWithFocus<OverlayItem>
    private lateinit var routes : List<Polyline>
    private lateinit var stats : StatsFragment
    private lateinit var db: Database
    private var selectedPoint : GeoPoint? = null
    private var routeOverlay : Polyline = Polyline()
    private var currentSpeed : Float = 0F
    private var maxSpeed : Float = 0F

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //load/initialize the osmdroid configuration, this can be done
        getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        setContentView(R.layout.activity_maps)

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        tLat = findViewById<TextView>(R.id.textLatitude)
        tLong = findViewById<TextView>(R.id.textLongtitude)
        map = findViewById<MapView>(R.id.map)
        btFollowMe = findViewById(R.id.btnFollow)
        stats = supportFragmentManager.findFragmentById(R.id.statsFragment) as StatsFragment
        btFollowMe.setOnClickListener {
            locationOverlay.enableFollowLocation()
        }

        roadManager = OSRMRoadManager(this, "MY_USER_AGENT")
        (roadManager as OSRMRoadManager).setMean(OSRMRoadManager.MEAN_BY_BIKE)

        InitMapConfiguration()
        map.invalidate()

        OfflineMapLoader()

        // LocationManager for changes
        CreateLocationManager()
        CreateLocationOverlay()
        CreateRotationOverlay()
        CreateCompassOverlay()
        CreateScaleBarOverlay()
        CreateEventsOverlay()
        // Bike paths:
        CreatePolyLinesOverlay()
        // Markerks overlay
        db = Database(this)
        CreatePointsOverlay()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val menuInflater = menuInflater
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId){
            R.id.dir -> {
                if (selectedPoint != null)
                    CreateRouteOverlay(selectedPoint!!)
                true
            }
            R.id.add -> {
                db.addData(selectedPoint!!.latitude, selectedPoint!!.longitude)
                pointsOverlay.addItem(OverlayItem("", "", selectedPoint))
                true
            }
            R.id.stats -> {
                stats.SwitchVisibility()
                true
            }
            R.id.left -> {
                for (route in routes){
                    if (map.overlays.contains(route))
                        map.overlays.remove(route)
                }
                map.overlays.add(routes[0])
                true
            }
            R.id.irpin -> {
                for (route in routes){
                    if (map.overlays.contains(route))
                        map.overlays.remove(route)
                }
                map.overlays.add(routes[1])
                true
            }
            R.id.center -> {
                for (route in routes){
                    if (map.overlays.contains(route))
                        map.overlays.remove(route)
                }
                map.overlays.add(routes[2])
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun InitRoutes(){
        val geoPointsIrpin = arrayOf(
            GeoPoint(50.465939, 30.355793),
            GeoPoint(50.475526, 30.358969),
            GeoPoint(50.473846, 30.334078),
            GeoPoint(50.473095, 30.331417),
            GeoPoint(50.481875, 30.291935),
            GeoPoint(50.482203, 30.282687),
            GeoPoint(50.490231, 30.260285),
            GeoPoint(50.491364, 30.259406),
            GeoPoint(50.492579, 30.260865),
            GeoPoint(50.495428, 30.257152),
            GeoPoint(50.499987, 30.256337),
            GeoPoint(50.504000, 30.257667),
            GeoPoint(50.506879, 30.256187),
            GeoPoint(50.509923, 30.258418),
            GeoPoint(50.514344, 30.257903),
            GeoPoint(50.517823, 30.249127),
            GeoPoint(50.539855, 30.263173),
            GeoPoint(50.549345, 30.264718),
            GeoPoint(50.553313, 30.278751),
            GeoPoint(50.553504, 30.293858),
            GeoPoint(50.518928, 30.352528),
            GeoPoint(50.515599, 30.361926),
            GeoPoint(50.495544, 30.365396))
        val lineIrpin = Polyline()
        lineIrpin.setPoints(geoPointsIrpin.toMutableList())
        lineIrpin.outlinePaint.color = Color.RED

        val geoPointsLeft = arrayOf(
            GeoPoint(50.455423, 30.532914),
            GeoPoint(50.466597, 30.549329),
            GeoPoint(50.467402, 30.551389),
            GeoPoint(50.470271, 30.551067),
            GeoPoint(50.477645, 30.547827),
            GeoPoint(50.480656, 30.549093),
            GeoPoint(50.487860, 30.545016),
            GeoPoint(50.490856, 30.541625),
            GeoPoint(50.491457, 30.541937),
            GeoPoint(50.491696, 30.544994),
            GeoPoint(50.491928, 30.545091),
            GeoPoint(50.492631, 30.544404),
            GeoPoint(50.493314, 30.545166),
            GeoPoint(50.495518, 30.563008),
            GeoPoint(50.495245, 30.587577),
            GeoPoint(50.494276, 30.589658),
            GeoPoint(50.464671, 30.599851),
            GeoPoint(50.462131, 30.600109),
            GeoPoint(50.454774, 30.607061),
            GeoPoint(50.451987, 30.598097),
            GeoPoint(50.450283, 30.597716),
            GeoPoint(50.448452, 30.591944),
            GeoPoint(50.445951, 30.590957),
            GeoPoint(50.441879, 30.605033),
            GeoPoint(50.438627, 30.608123),
            GeoPoint(50.432596, 30.592738),
            GeoPoint(50.430330, 30.592186),
            GeoPoint(50.424213, 30.571468),
            GeoPoint(50.424781, 30.568840),
            GeoPoint(50.436288, 30.563859),
            GeoPoint(50.444406, 30.556049),
            GeoPoint(50.450746, 30.544548),
            GeoPoint(50.455423, 30.532914))
        val lineLeft = Polyline()
        lineLeft.setPoints(geoPointsLeft.toMutableList())
        lineLeft.outlinePaint.color = Color.GREEN

        val geoPointsCenter = arrayOf(
            GeoPoint(50.452461, 30.467455),
            GeoPoint(50.460811, 30.486760),
            GeoPoint(50.460906, 30.488724),
            GeoPoint(50.462326, 30.490204),
            GeoPoint(50.462334, 30.501609),
            GeoPoint(50.463100, 30.505053),
            GeoPoint(50.462568, 30.507027),
            GeoPoint(50.461365, 30.518314),
            GeoPoint(50.460642, 30.520846),
            GeoPoint(50.461038, 30.521661),
            GeoPoint(50.456311, 30.527991),
            GeoPoint(50.454699, 30.527391),
            GeoPoint(50.452691, 30.527884),
            GeoPoint(50.448920, 30.522713),
            GeoPoint(50.447698, 30.522053),
            GeoPoint(50.448894, 30.514494),
            GeoPoint(50.436021, 30.509345),
            GeoPoint(50.436500, 30.505890),
            GeoPoint(50.446667, 30.494024),
            GeoPoint(50.452078, 30.460249))
        val lineCenter = Polyline()
        lineCenter.setPoints(geoPointsCenter.toMutableList())
        lineCenter.outlinePaint.color = Color.YELLOW


        routes = listOf(lineLeft, lineIrpin, lineCenter)
    }

    fun InitMapConfiguration(){
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setBuiltInZoomControls(true)
        map.setMultiTouchControls(true)
        map.minZoomLevel = 12.0
        map.maxZoomLevel = 20.5
        val mapController = map.controller
        mapController.setZoom(15.5)
        val startPoint = GeoPoint(50.450001, 30.523333);
        mapController.setCenter(startPoint);
    }

    fun IsOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivityManager != null) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                    return true
                }
            }
        }
        return false
    }

    fun OfflineMapLoader(){
        val ctx: Context = applicationContext
        val rootDirectory = applicationContext.dataDir //getCacheDir()//Environment.getExternalStorageDirectory()
        val osmDir =
            rootDirectory.absolutePath + File.separator + "osmdroid" + File.separator + "tiles"
        val directory = File(osmDir)
        val osmdroidTile = directory.absolutePath + File.separator + "map.sqlite"
        val f = File(osmdroidTile)

        if (!f.exists()) {
            map.setTileSource(TileSourceFactory.OpenTopo)
            directory.mkdirs()
            f.writeText("")
            val writer = SqliteArchiveTileWriter(osmdroidTile)
            val cacheManager = CacheManager(map, writer)
            cacheManager.downloadAreaAsync(ctx, map.boundingBox,
                map.minZoomLevel.toInt(),
                map.maxZoomLevel.toInt() + 5,
                object : CacheManagerCallback {
                    override fun onTaskComplete() {writer?.onDetach() }
                    override fun updateProgress(
                        progress: Int, currentZoomLevel: Int,
                        zoomMin: Int, zoomMax: Int) {}
                    override fun downloadStarted() {}
                    override fun setPossibleTilesInArea(total: Int) {}
                    override fun onTaskFailed(errors: Int) {writer?.onDetach()}
                })
            map.setTileSource(TileSourceFactory.MAPNIK)
        }
        if(!IsOnline(ctx)) {
            val tileProvider = OfflineTileProvider(
                SimpleRegisterReceiver(ctx), arrayOf(f))
            val archives = tileProvider.getArchives()
            if (archives.isNotEmpty()) {
                val tileSources = archives[0].tileSources;
                if (tileSources.isNotEmpty()) {

                    val source = tileSources.iterator().next();
                    map.setTileSource(FileBasedTileSource.getSource(source));
                } else {
                    map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
                }
            }
            map.setUseDataConnection(false)
            map.invalidate()
        }
    }

    fun CreateEventsOverlay(){
        mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {

            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                Log.d("singleTapConfirmedHelper", "${p?.latitude} - ${p?.longitude}")
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                selectedPoint = p
                return true
            }})
        map.overlays.add(mapEventsOverlay)
    }

    fun CreateRouteOverlay(endPoint: GeoPoint){
        val waypoints = ArrayList<GeoPoint>()
        waypoints.add(locationOverlay.myLocation)
        waypoints.add(endPoint)
        val road = roadManager.getRoad(waypoints)
        if(map.overlays.contains(routeOverlay))
            map.overlays.remove(routeOverlay)
        routeOverlay = RoadManager.buildRoadOverlay(road)
        routeOverlay.outlinePaint.strokeWidth = 15F
        map.overlays.add(routeOverlay)
        map.invalidate()
    }

    fun CreateRotationOverlay(){
        rotationGestureOverlay = RotationGestureOverlay(map);
        rotationGestureOverlay.setEnabled(true);
        //map.overlays.add(rotationGestureOverlay)
    }

    fun CreateLocationOverlay(){
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider((applicationContext)), map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
    }

    fun CreateCompassOverlay(){
        compassOverlay = CompassOverlay(applicationContext,
            InternalCompassOrientationProvider(applicationContext), map)
        compassOverlay.enableCompass()
        map.overlays.add(compassOverlay)
    }

    fun CreateScaleBarOverlay(){
        val dm : DisplayMetrics = applicationContext.resources.displayMetrics
        scaleBarOverlay = ScaleBarOverlay(map)
        scaleBarOverlay.setCentred(true)
        scaleBarOverlay.setTextSize(16F)
        scaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10)
        map.overlays.add(scaleBarOverlay)
    }

    fun CreatePointsOverlay(){
        val items = ArrayList<OverlayItem>()
        val points = db.getArrayListOfPoints()
        for(point in points)
            items.add(OverlayItem("", "", GeoPoint(point.lat, point.long)))
        pointsOverlay = ItemizedOverlayWithFocus<OverlayItem>(items,
            object: ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                override fun onItemSingleTapUp(index:Int, item:OverlayItem):Boolean {
                    return false
                }
                override fun onItemLongPress(index:Int, item:OverlayItem):Boolean {
                    db.deleteData(item.point!!.latitude, item.point!!.longitude)
                    pointsOverlay.removeItem(item)
                    return true
                }
            }, applicationContext)
        //pointsOverlay.setFocusItemsOnTap(true);
        map.overlays.add(pointsOverlay);
    }

    fun CreatePolyLinesOverlay(){
        val pravdy = arrayOf(
            GeoPoint(50.507661, 30.406813),
            GeoPoint(50.509617, 30.396797))

        val paladina = arrayOf(
            GeoPoint(50.499091, 30.364977),
            GeoPoint(50.492962, 30.365417),
            GeoPoint(50.480168, 30.360274),
            GeoPoint(50.479895, 30.359201),
            GeoPoint(50.478762, 30.359244),
            GeoPoint(50.476467, 30.358343),
            GeoPoint(50.476194, 30.359073),
            GeoPoint(50.468451, 30.356047),
            GeoPoint(50.465173, 30.355811),
            GeoPoint(50.460146, 30.356653))

        val warshawskii = arrayOf(
            GeoPoint(50.5022062184717, 30.42542481400102),
            GeoPoint(50.502930, 30.424277),
            GeoPoint(50.503656, 30.424626),
            GeoPoint(50.504192, 30.422507),
            GeoPoint(50.502196, 30.421530),
            GeoPoint(50.503131, 30.421992),
            GeoPoint(50.502704, 30.424143),
            GeoPoint(50.501872, 30.423907))

        val nyvky = arrayOf(
            GeoPoint(50.482952, 30.395749),
            GeoPoint(50.464481, 30.396006))

        val salutna = arrayOf(
            GeoPoint(50.471959, 30.398656),
            GeoPoint(50.471968, 30.404753))

        val nazhytomyr = arrayOf(
            GeoPoint(50.454125, 30.338508),
            GeoPoint(50.454714, 30.349323))

        val chornobylska = arrayOf(
            GeoPoint(50.455197, 30.346640),
            GeoPoint(50.459694, 30.345826))

        val sviatoshyn = arrayOf(
            GeoPoint(50.455405, 30.359672),
            GeoPoint(50.457155, 30.385185))

        val prospectNaNyvkah = arrayOf(
            GeoPoint(50.457616, 30.393019),
            GeoPoint(50.458924, 30.412361))

        val softserve = arrayOf(
            GeoPoint(50.457613, 30.425962),
            GeoPoint(50.454480, 30.445562))

        val kpi = arrayOf(
            GeoPoint(50.452034, 30.445108),
            GeoPoint(50.453181, 30.445602),
            GeoPoint(50.453472, 30.446632),
            GeoPoint(50.454233, 30.447259),
            GeoPoint(50.448813, 30.479859))

        val rybalka = arrayOf(
            GeoPoint(50.454247, 30.484617),
            GeoPoint(50.456044, 30.474328))

        val parkIunist = arrayOf(
            GeoPoint(50.428078, 30.381365),
            GeoPoint(50.428834, 30.384490),
            GeoPoint(50.428771, 30.384817),
            GeoPoint(50.427775, 30.383709),
            GeoPoint(50.427436, 30.384302),
            GeoPoint(50.426061, 30.384221))

        val ato = arrayOf(
            GeoPoint(50.430264, 30.392089),
            GeoPoint(50.430988, 30.392481),
            GeoPoint(50.431224, 30.393511),
            GeoPoint(50.430705, 30.393892),
            GeoPoint(50.430274, 30.392106))

        val kurbasa1 = arrayOf(
            GeoPoint(50.433672, 30.402057),
            GeoPoint(50.435775, 30.410395))

        val kurbasa2 = arrayOf(
            GeoPoint(50.444025, 30.410460),
            GeoPoint(50.438990, 30.413636),
            GeoPoint(50.440958, 30.412370),
            GeoPoint(50.440179, 30.409194),
            GeoPoint(50.441955, 30.416382),
            GeoPoint(50.445030, 30.414494),
            GeoPoint(50.438170, 30.418807),
            GeoPoint(50.437186, 30.414859),
            GeoPoint(50.441586, 30.432132),
            GeoPoint(50.443144, 30.431317),
            GeoPoint(50.445904, 30.432368))

        val kurbasa3 = arrayOf(
            GeoPoint(50.436653, 30.413239),
            GeoPoint(50.437883, 30.418946),
            GeoPoint(50.434022, 30.421360),
            GeoPoint(50.432054, 30.413732),
            GeoPoint(50.430229, 30.414880),
            GeoPoint(50.432170, 30.422519),
            GeoPoint(50.430570, 30.424225),
            GeoPoint(50.429655, 30.426467),
            GeoPoint(50.428752, 30.430008),
            GeoPoint(50.429655, 30.426467),
            GeoPoint(50.430570, 30.424225),
            GeoPoint(50.432170, 30.422519),
            GeoPoint(50.437869, 30.419064),
            GeoPoint(50.441279, 30.432272),
            GeoPoint(50.436538, 30.435351))

        val vidradnii = arrayOf(
            GeoPoint(50.43284, 30.42379),
            GeoPoint(50.43297, 30.42364),
            GeoPoint(50.43394, 30.42407),
            GeoPoint(50.43408, 30.42438),
            GeoPoint(50.43410, 30.42465),
            GeoPoint(50.43448, 30.42502),
            GeoPoint(50.43471, 30.42511),
            GeoPoint(50.43487, 30.42505),
            GeoPoint(50.43479, 30.42485),
            GeoPoint(50.43479, 30.42485),
            GeoPoint(50.43289, 30.42395),
            GeoPoint(50.43284, 30.42379))

        val tedeia = arrayOf(
            GeoPoint(50.371802, 30.443597),
            GeoPoint(50.371090, 30.444809),
            GeoPoint(50.374437, 30.451214),
            GeoPoint(50.374231, 30.452588))

        val okruzhna = arrayOf(
            GeoPoint(50.367262, 30.462726),
            GeoPoint(50.362297, 30.469680),
            GeoPoint(50.358680, 30.473197),
            GeoPoint(50.357123, 30.475902),
            GeoPoint(50.355579, 30.481147),
            GeoPoint(50.354433, 30.491470))

        val pyrohiv = arrayOf(
            GeoPoint(50.353378, 30.504924),
            GeoPoint(50.352221, 30.505568),
            GeoPoint(50.350722, 30.510449),
            GeoPoint(50.351427, 30.510288),
            GeoPoint(50.352571, 30.511082),
            GeoPoint(50.354039, 30.513223),
            GeoPoint(50.354138, 30.515717),
            GeoPoint(50.355596, 30.517166))

        val vdnh = arrayOf(
            GeoPoint(50.376747, 30.480527),
            GeoPoint(50.376921, 30.481052),
            GeoPoint(50.379070, 30.478818),
            GeoPoint(50.380530, 30.482611),
            GeoPoint(50.380428, 30.483083),
            GeoPoint(50.380099, 30.483002),
            GeoPoint(50.377493, 30.476212),
            GeoPoint(50.373476, 30.474399),
            GeoPoint(50.377493, 30.476212),
            GeoPoint(50.377900, 30.475907),
            GeoPoint(50.379083, 30.478771),
            GeoPoint(50.379791, 30.478090),
            GeoPoint(50.379615, 30.477639),
            GeoPoint(50.378872, 30.478265),
            GeoPoint(50.378321, 30.478520))

        val erevan = arrayOf(
            GeoPoint(50.441799, 30.467491),
            GeoPoint(50.440159, 30.466504),
            GeoPoint(50.436333, 30.468597),
            GeoPoint(50.434863, 30.472223),
            GeoPoint(50.436927, 30.474283),
            GeoPoint(50.438718, 30.469187),
            GeoPoint(50.438075, 30.465367),
            GeoPoint(50.441068, 30.464058),
            GeoPoint(50.438075, 30.465367),
            GeoPoint(50.436989, 30.460174),
            GeoPoint(50.431795, 30.455888))

        val soloma = arrayOf(
            GeoPoint(50.426860, 30.492189),
            GeoPoint(50.422882, 30.487039),
            GeoPoint(50.422144, 30.483306),
            GeoPoint(50.420504, 30.487318),
            GeoPoint(50.420709, 30.489915),
            GeoPoint(50.420504, 30.487318),
            GeoPoint(50.424742, 30.477512),
            GeoPoint(50.427454, 30.475894),
            GeoPoint(50.431146, 30.471880))

        val borshchahivska = arrayOf(
            GeoPoint(50.447689, 30.477314),
            GeoPoint(50.447839, 30.474710),
            GeoPoint(50.445578, 30.462111),
            GeoPoint(50.446561, 30.453056),
            GeoPoint(50.445222, 30.447058),
            GeoPoint(50.446561, 30.453056),
            GeoPoint(50.447866, 30.453181),
            GeoPoint(50.447285, 30.459908),
            GeoPoint(50.448402, 30.463695))

        val franc = arrayOf(
            GeoPoint(50.416271, 30.526090),
            GeoPoint(50.415909, 30.526246),
            GeoPoint(50.415516, 30.524357),
            GeoPoint(50.416107, 30.527426),
            GeoPoint(50.414735, 30.528094))

        val novoPechersk = arrayOf(
            GeoPoint(50.416745, 30.542505),
            GeoPoint(50.415514, 30.544050),
            GeoPoint(50.415555, 30.546325),
            GeoPoint(50.410373, 30.546142),
            GeoPoint(50.409799, 30.545745),
            GeoPoint(50.409040, 30.548320),
            GeoPoint(50.409799, 30.545745),
            GeoPoint(50.408896, 30.544243))

        val bastion = arrayOf(
            GeoPoint(50.419261, 30.548572),
            GeoPoint(50.417743, 30.553384),
            GeoPoint(50.415494, 30.556506),
            GeoPoint(50.414708, 30.556388))

        val palacSportu = arrayOf(
            GeoPoint(50.433997, 30.518312),
            GeoPoint(50.438213, 30.518392),
            GeoPoint(50.440263, 30.522673),
            GeoPoint(50.438609, 30.524840),
            GeoPoint(50.439170, 30.525055),
            GeoPoint(50.440755, 30.522630),
            GeoPoint(50.442600, 30.522136),
            GeoPoint(50.442819, 30.520870),
            GeoPoint(50.442163, 30.520323),
            GeoPoint(50.441835, 30.522254))

        val pechersk = arrayOf(
            GeoPoint(50.438589, 30.525205),
            GeoPoint(50.428060, 30.538520),
            GeoPoint(50.429700, 30.536460),
            GeoPoint(50.430616, 30.538070),
            GeoPoint(50.430186, 30.538960),
            GeoPoint(50.431814, 30.541232),
            GeoPoint(50.431464, 30.541988),
            GeoPoint(50.432159, 30.542144),
            GeoPoint(50.431978, 30.544574),
            GeoPoint(50.432402, 30.545121),
            GeoPoint(50.432289, 30.546259),
            GeoPoint(50.433329, 30.550350),
            GeoPoint(50.433367, 30.552013),
            GeoPoint(50.433189, 30.553043),
            GeoPoint(50.433479, 30.554776),
            GeoPoint(50.432102, 30.556755),
            GeoPoint(50.431750, 30.555977),
            GeoPoint(50.435190, 30.554194),
            GeoPoint(50.440418, 30.550176),
            GeoPoint(50.438323, 30.547467),
            GeoPoint(50.439621, 30.546710),
            GeoPoint(50.440418, 30.550176),
            GeoPoint(50.443717, 30.545356),
            GeoPoint(50.442340, 30.544766),
            GeoPoint(50.434951, 30.545058),
            GeoPoint(50.434080, 30.547885),
            GeoPoint(50.432723, 30.548272),
            GeoPoint(50.432334, 30.545064),
            GeoPoint(50.434958, 30.544989),
            GeoPoint(50.432334, 30.545064),
            GeoPoint(50.429791, 30.541770))

        val khreschatyk = arrayOf(
            GeoPoint(50.449232, 30.523166),
            GeoPoint(50.448268, 30.525687),
            GeoPoint(50.446431, 30.527479),
            GeoPoint(50.447763, 30.529346),
            GeoPoint(50.446041, 30.526846),
            GeoPoint(50.446663, 30.525387),
            GeoPoint(50.447558, 30.526245),
            GeoPoint(50.445604, 30.524207))

        val center = arrayOf(
            GeoPoint(50.447719, 30.522090),
            GeoPoint(50.448113, 30.519338),
            GeoPoint(50.439551, 30.515845),
            GeoPoint(50.442797, 30.517132),
            GeoPoint(50.442355, 30.519996),
            GeoPoint(50.443516, 30.512401),
            GeoPoint(50.440345, 30.511049),
            GeoPoint(50.443516, 30.512401),
            GeoPoint(50.446843, 30.493355),
            GeoPoint(50.450108, 30.502839),
            GeoPoint(50.449261, 30.503590),
            GeoPoint(50.449475, 30.506532),
            GeoPoint(50.448986, 30.509499),
            GeoPoint(50.444381, 30.507568),
            GeoPoint(50.444217, 30.509134),
            GeoPoint(50.446513, 30.510185))

        val center2 = arrayOf(
            GeoPoint(50.450174, 30.502899),
            GeoPoint(50.452083, 30.508573),
            GeoPoint(50.454201, 30.506084),
            GeoPoint(50.449255, 30.512328),
            GeoPoint(50.448941, 30.514495),
            GeoPoint(50.449112, 30.513369),
            GeoPoint(50.450919, 30.514163),
            GeoPoint(50.451911, 30.510854),
            GeoPoint(50.451154, 30.509876),
            GeoPoint(50.451911, 30.510854),
            GeoPoint(50.452774, 30.509467),
            GeoPoint(50.452123, 30.508571),
            GeoPoint(50.454133, 30.511226),
            GeoPoint(50.455315, 30.511564),
            GeoPoint(50.455410, 30.516934),
            GeoPoint(50.454126, 30.516676),
            GeoPoint(50.458334, 30.518136),
            GeoPoint(50.457020, 30.517680),
            GeoPoint(50.457331, 30.515564),
            GeoPoint(50.456877, 30.515365),
            GeoPoint(50.456248, 30.515451),
            GeoPoint(50.455965, 30.51473),
            GeoPoint(50.456204, 30.513976),
            GeoPoint(50.456143, 30.512951),
            GeoPoint(50.456409, 30.512179),
            GeoPoint(50.456259, 30.511546),
            GeoPoint(50.456293, 30.510988),
            GeoPoint(50.455934, 30.510827),
            GeoPoint(50.455726, 30.509416),
            GeoPoint(50.455367, 30.508735),
            GeoPoint(50.455125, 30.508713),
            GeoPoint(50.455306, 30.511562),
            GeoPoint(50.455097, 30.508225),
            GeoPoint(50.455364, 30.505918),
            GeoPoint(50.456449, 30.501865),
            GeoPoint(50.456131, 30.490471),
            GeoPoint(50.457889, 30.487196))

        val volodymyr = arrayOf(
            GeoPoint(50.455023, 30.520424),
            GeoPoint(50.453705, 30.517023))

        val podil = arrayOf(
            GeoPoint(50.459791, 30.526797),
            GeoPoint(50.463397, 30.525853),
            GeoPoint(50.461034, 30.521625),
            GeoPoint(50.463397, 30.525853),
            GeoPoint(50.469297, 30.524029),
            GeoPoint(50.470608, 30.522012),
            GeoPoint(50.469625, 30.523278),
            GeoPoint(50.468218, 30.521097),
            GeoPoint(50.467331, 30.522537),
            GeoPoint(50.467952, 30.523567),
            GeoPoint(50.464985, 30.518418),
            GeoPoint(50.466268, 30.520980),
            GeoPoint(50.464424, 30.523855),
            GeoPoint(50.468835, 30.516796))

        val pochaina = arrayOf(
            GeoPoint(50.488089, 30.478987),
            GeoPoint(50.489208, 30.489330),
            GeoPoint(50.488253, 30.526065),
            GeoPoint(50.489263, 30.530872),
            GeoPoint(50.490734, 30.529777),
            GeoPoint(50.492389, 30.526822),
            GeoPoint(50.501780, 30.517611),
            GeoPoint(50.501548, 30.522224),
            GeoPoint(50.499937, 30.524392),
            GeoPoint(50.498436, 30.524928),
            GeoPoint(50.496170, 30.527160),
            GeoPoint(50.494737, 30.528125),
            GeoPoint(50.493741, 30.527267))

        val obolon = arrayOf(
            GeoPoint(50.524738, 30.503207),
            GeoPoint(50.524564, 30.513481))


        val okruzhnaNorth = arrayOf(
            GeoPoint(50.529256, 30.480539),
            GeoPoint(50.529065, 30.470732),
            GeoPoint(50.526559, 30.465121),
            GeoPoint(50.527105, 30.460336),
            GeoPoint(50.526559, 30.465121),
            GeoPoint(50.528380, 30.469123),
            GeoPoint(50.505845, 30.468878))


        val geoPoints = arrayOf(pravdy, paladina, warshawskii, nyvky,
            salutna, nazhytomyr, chornobylska, sviatoshyn,
            prospectNaNyvkah,  softserve, kpi, rybalka,
            parkIunist, ato, kurbasa1, kurbasa2, kurbasa3,
            vidradnii, tedeia, okruzhna, pyrohiv, vdnh,
            erevan, soloma, borshchahivska, franc,
            novoPechersk, bastion, palacSportu, pechersk,
            khreschatyk, center, center2, volodymyr,
            podil, pochaina, obolon, okruzhnaNorth)

        for(road in geoPoints){
            val line = Polyline()
            line.setPoints(road.toMutableList());
            //line.outlinePaint.color = Color.LTGRAY
            line.outlinePaint.alpha = 100
            map.overlays.add(line)
        }
        InitRoutes()
    }

    fun CreateLocationManager(){
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val criteria = Criteria()
        criteria.setAccuracy(Criteria.ACCURACY_COARSE)
        criteria.setAccuracy(Criteria.ACCURACY_FINE)
        val provider = locationManager.getBestProvider(criteria, true)
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                var permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    REQUEST_PERMISSIONS_REQUEST_CODE);
            return
        }
        locationManager.requestLocationUpdates(provider!!,
            5000, 20F, this)
        //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, this)
    }

    override fun onResume() {
        super.onResume()
        //locationManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager

        locationOverlay.enableFollowLocation()
        locationOverlay.enableMyLocation()
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume() //needed for compass, location overlays

        // TODO resuming from on pause position and zoom
    }

    override fun onPause() {
        super.onPause()
        //all same as onResume
        map.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsToRequest = ArrayList<String>()
        var i = 0
        while (i < grantResults.size) {
            permissionsToRequest.add(permissions[i])
            i++
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }

    fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier
    }

    override fun onLocationChanged(loc: Location) {
        currentSpeed = loc.speed
        if(maxSpeed < currentSpeed)
            maxSpeed = currentSpeed
        val lat = loc.latitude
        val long = loc.longitude
        if(locationOverlay.myLocation != null) {
            locationOverlay.myLocation.latitude = lat;
            locationOverlay.myLocation.longitude = long;
        }
        tLat.text = lat.round(7).toString()
        tLong.text = long.round(7).toString()
        stats.SetText(currentSpeed, maxSpeed)
    }

    override fun onProviderDisabled(provider: String) {
        Toast.makeText(
            applicationContext, "Gps Disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onProviderEnabled(provider: String) {
        Toast.makeText(applicationContext, "Gps Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

}