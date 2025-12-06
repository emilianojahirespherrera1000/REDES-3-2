package com.example.proyectoredes3

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton

// IMPORTACIONES DE OPENSTREETMAP
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    // UI Elements
    private lateinit var tvSheetTitle: TextView
    private lateinit var tvSheetSubtitle: TextView
    private lateinit var tvEta: TextView
    private lateinit var tvStatus: TextView
    private lateinit var detailsContainer: LinearLayout

    private val markers = mutableListOf<Marker>()
    private val handler = Handler(Looper.getMainLooper())

    // Datos simulados
    private val mockBuses = listOf(
        MockBus("A-107", 22.1450, -102.2740, "15 min", "En Ruta", "Cifrado AES-256"),
        MockBus("A-108", 22.0600, -102.2850, "30 min", "En Ruta", "Sync Google Drive"),
        MockBus("A-109", 21.9400, -102.3000, "5 min", "Llegando", "NFS Replicado")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- CONFIGURACIÓN DE OPENSTREETMAP ---
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        // CORRECCIÓN AQUÍ: Ponemos el nombre del paquete directo
        Configuration.getInstance().userAgentValue = "com.example.proyectoredes3"

        setContentView(R.layout.activity_main)

        setupUI()
        setupMap()
    }

    private fun setupUI() {
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.peekHeight = 220

        tvSheetTitle = findViewById(R.id.tvSheetTitle)
        tvSheetSubtitle = findViewById(R.id.tvSheetSubtitle)
        tvEta = findViewById(R.id.tvEta)
        tvStatus = findViewById(R.id.tvStatus)
        detailsContainer = findViewById(R.id.detailsContainer)

        findViewById<FloatingActionButton>(R.id.fabRecenter).setOnClickListener {
            val centro = GeoPoint(22.03, -102.28)
            map.controller.animateTo(centro)
            map.controller.setZoom(11.0)
        }
    }

    private fun setupMap() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val startPoint = GeoPoint(22.03, -102.28)
        map.controller.setZoom(11.0)
        map.controller.setCenter(startPoint)

        initializeMarkers()
        startSimulationLoop()
    }

    private fun initializeMarkers() {
        // En caso de error cargando el icono, usamos uno genérico para que no falle la app
        val iconDrawable = try {
            getIconDrawable(R.drawable.ic_bus_vector)
        } catch (e: Exception) {
            ContextCompat.getDrawable(this, android.R.drawable.ic_menu_myplaces)
        }

        for (bus in mockBuses) {
            val marker = Marker(map)
            marker.position = GeoPoint(bus.lat, bus.lng)
            marker.title = "Unidad ${bus.id}"
            marker.icon = iconDrawable
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            marker.setOnMarkerClickListener { m, _ ->
                showBusDetails(bus, m)
                true
            }
            map.overlays.add(marker)
            markers.add(marker)
        }
        map.invalidate()
    }

    private fun showBusDetails(bus: MockBus, marker: Marker) {
        tvSheetTitle.text = "Combi ${bus.id}"
        tvSheetSubtitle.text = "Estado: ${bus.status}"
        tvEta.text = bus.eta
        tvStatus.text = bus.serverStatus

        detailsContainer.visibility = View.VISIBLE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        map.controller.animateTo(marker.position)
    }

    private fun startSimulationLoop() {
        val updateRunnable = object : Runnable {
            override fun run() {
                animateMovement()
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(updateRunnable)
    }

    private fun animateMovement() {
        for ((index, marker) in markers.withIndex()) {
            val start = marker.position
            val bus = mockBuses[index]

            val newLat = start.latitude - 0.002
            val newLng = start.longitude + (Math.random() * 0.0002 - 0.0001)

            var endLat = newLat
            var endLng = newLng

            if (endLat < 21.85) {
                endLat = 22.15
                endLng = -102.27
            }

            bus.lat = endLat
            bus.lng = endLng

            val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
            valueAnimator.duration = 3000
            valueAnimator.interpolator = LinearInterpolator()
            valueAnimator.addUpdateListener { va ->
                val v = va.animatedFraction
                val lng = v * endLng + (1 - v) * start.longitude
                val lat = v * endLat + (1 - v) * start.latitude
                marker.position = GeoPoint(lat, lng)
                map.invalidate()
            }
            valueAnimator.start()
        }
    }

    private fun getIconDrawable(resId: Int): Drawable {
        val vectorDrawable = ContextCompat.getDrawable(this, resId)!!
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
}

data class MockBus(
    val id: String,
    var lat: Double,
    var lng: Double,
    val eta: String,
    val status: String,
    val serverStatus: String
)