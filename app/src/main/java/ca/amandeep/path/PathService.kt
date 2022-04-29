package ca.amandeep.path

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
        val INSTANCE: PathApiService by lazy {
            Retrofit.Builder()
                .baseUrl("https://path.api.razza.dev/v1/")
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