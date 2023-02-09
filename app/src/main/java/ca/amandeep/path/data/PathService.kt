package ca.amandeep.path.data

import ca.amandeep.path.Stations
import ca.amandeep.path.UpcomingTrains
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.*

interface PathApiService {
    @GET("stations")
    suspend fun listStations(): Stations

    @GET("stations/{station}/realtime")
    suspend fun getArrivals(@Path("station") station: String): UpcomingTrains

    companion object {
        private const val API_PATH = "https://path.api.razza.dev/v1/"

        val INSTANCE: PathApiService by lazy {
            Retrofit.Builder()
                .baseUrl(API_PATH)
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder()
                            .add(Date::class.java, Rfc3339DateJsonAdapter())
                            .build()
                    )
                )
                .build()
                .create(PathApiService::class.java)
        }
    }
}