package com.example.proyectoredes3

import retrofit2.Call
import retrofit2.http.GET

class ApiService {
    // Modelo de datos
    data class BusResponse(
        val busId: String,
        val lat: Double,
        val lon: Double,
        val estado: String? // Agregamos estado para mostrar si va retrasado
    )

    interface ApiService {
        // CAMBIO IMPORTANTE: Ahora devuelve una LISTA (List<BusResponse>)
        @GET("api/obtener-ubicacion")
        fun getUbicacion(): Call<List<BusResponse>>
    }
}