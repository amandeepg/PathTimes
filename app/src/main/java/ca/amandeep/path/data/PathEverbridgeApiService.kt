package ca.amandeep.path.data

import ca.amandeep.path.data.model.Alerts
import ca.amandeep.path.data.model.Stations
import ca.amandeep.path.data.model.UpcomingTrains
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.Date

interface PathEverbridgeApiService {
    @GET("incidents?status=All&department=Path")
    suspend fun getAlerts(): Alerts

    companion object {
        private const val API_PATH = "https://www.panynj.gov/bin/portauthority/everbridge/"

        val INSTANCE: PathEverbridgeApiService by lazy {
            Retrofit.Builder()
                .baseUrl(API_PATH)
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder()
                            .add(Date::class.java, UnixTimestampDateJsonAdapter())
                            .build(),
                    ),
                )
                .build()
                .create(PathEverbridgeApiService::class.java)
        }
    }
}
