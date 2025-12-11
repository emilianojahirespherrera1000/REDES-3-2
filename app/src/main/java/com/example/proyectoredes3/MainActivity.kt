package com.example.proyectoredes3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton

// IMPORTACIONES OSM
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// IMPORTACIONES RETROFIT
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    // UI Elements
    private lateinit var tvSheetTitle: TextView
    private lateinit var tvSheetSubtitle: TextView
    private lateinit var tvEta: TextView
    private lateinit var tvStatus: TextView
    private lateinit var detailsContainer: LinearLayout

    // CAMBIO: Usamos un MAPA para rastrear múltiples marcadores por su ID
    private val busMarkers = mutableMapOf<String, Marker>()

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var apiService: ApiService.ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = "com.example.proyectoredes3"

        setContentView(R.layout.activity_main)

        setupRetrofit()
        setupUI()
        setupMap()
    }

    private fun setupRetrofit() {
        // ⚠️ REVISA QUE ESTA SEA TU IP ACTUAL DE LA VM APP (ej. 192.168.100.156)
        val retrofit = Retrofit.Builder()
            .baseUrl(Config.BASE_URL) // <--- ¡AQUÍ ESTÁ EL CAMBIO!
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService.ApiService::class.java)
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
            val centro = GeoPoint(21.885, -102.291)
            map.controller.animateTo(centro)
            map.controller.setZoom(14.0)
        }
    }

    private fun setupMap() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Centro inicial (Aguascalientes)
        val startPoint = GeoPoint(21.885, -102.291)
        map.controller.setZoom(14.0)
        map.controller.setCenter(startPoint)

        // Ya no llamamos a initializeMarker() porque los marcadores se crean dinámicamente
        startNetworkLoop()
    }

    private fun startNetworkLoop() {
        val updateRunnable = object : Runnable {
            override fun run() {
                fetchBusData()
                handler.postDelayed(this, 3000) // Se actualiza cada 3 segundos
            }
        }
        handler.post(updateRunnable)
    }

    private fun fetchBusData() {
        // Solicitamos la lista
        apiService.getUbicacion().enqueue(object : Callback<List<ApiService.BusResponse>> {
            override fun onResponse(call: Call<List<ApiService.BusResponse>>, response: Response<List<ApiService.BusResponse>>) {
                if (response.isSuccessful && response.body() != null) {
                    val busesList = response.body()!!
                    updateAllMarkers(busesList)

                    // Actualizamos el status general
                    tvStatus.text = "SISTEMA ONLINE - ${busesList.size} Unidades Activas"

                    // Opcional: Si solo hay 1 bus o queremos mostrar info del primero en el panel
                    if (busesList.isNotEmpty()) {
                        updateBottomSheetInfo(busesList[0])
                    }
                } else {
                    tvStatus.text = "Error Servidor: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<List<ApiService.BusResponse>>, t: Throwable) {
                tvStatus.text = "Desconectado: Buscando señal..."
            }
        })
    }

    private fun updateAllMarkers(buses: List<ApiService.BusResponse>) {
        // 1. Obtener la lista de IDs que están VIVOS en este momento
        val currentBusIds = buses.map { it.busId }

        // 2. LIMPIEZA: Eliminar marcadores que ya no existen en el servidor
        val iterator = busMarkers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val markerId = entry.key

            // Si el marcador que tengo en el mapa NO está en la lista nueva...
            if (!currentBusIds.contains(markerId)) {
                // ... lo borramos del mapa visualmente
                entry.value.remove(map)
                // ... y lo sacamos de mi diccionario
                iterator.remove()
            }
        }

        // 3. ACTUALIZAR O CREAR (Tu lógica normal)
        val iconDrawable = try {
            getIconDrawable(R.drawable.ic_bus_vector) // Asegúrate de tener este ícono o usa el default
        } catch (e: Exception) {
            ContextCompat.getDrawable(this, android.R.drawable.ic_menu_directions)
        }

        for (bus in buses) {
            val newPoint = GeoPoint(bus.lat, bus.lon)
            val busId = bus.busId

            if (busMarkers.containsKey(busId)) {
                val marker = busMarkers[busId]!!
                marker.position = newPoint
                marker.snippet = "Estado: ${bus.estado}"
                // Forzar redibujado del infowindow si está abierto
                if (marker.isInfoWindowShown) {
                    marker.showInfoWindow()
                }
            } else {
                val newMarker = Marker(map)
                newMarker.position = newPoint
                newMarker.icon = iconDrawable
                newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                newMarker.title = busId
                newMarker.snippet = bus.estado ?: "En ruta"

                newMarker.setOnMarkerClickListener { m, _ ->
                    val selectedBus = buses.find { it.busId == m.title }
                    if (selectedBus != null) updateBottomSheetInfo(selectedBus)
                    m.showInfoWindow()
                    true
                }

                map.overlays.add(newMarker)
                busMarkers[busId] = newMarker
            }
        }

        map.invalidate() // Refrescar mapa
    }

    private fun updateBottomSheetInfo(data: ApiService.BusResponse) {
        tvSheetTitle.text = "Unidad: ${data.busId}"
        tvSheetSubtitle.text = "Estado: ${data.estado}"
        tvEta.text = "En tránsito"

        if (detailsContainer.visibility != View.VISIBLE) {
            detailsContainer.visibility = View.VISIBLE
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