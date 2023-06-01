package ca.amandeep.path.data

import ca.amandeep.path.data.model.AlertContainer
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header

interface PathAlertsApiService {
    @GET("fetch?contentKey=PathAlert")
    suspend fun getAlerts(@Header("apikey") apiKey: String = API_KEY): AlertContainer

    companion object {
        private const val API_PATH = "https://path-mppprod-app.azurewebsites.net/api/v1/AppContent/"

        private const val API_KEY = "206A2FF9-0EA6-4C88-B030-5727054CB9DE"

        val INSTANCE: PathAlertsApiService by lazy {
            Retrofit.Builder()
                .baseUrl(API_PATH)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(PathAlertsApiService::class.java)
        }
    }
}
