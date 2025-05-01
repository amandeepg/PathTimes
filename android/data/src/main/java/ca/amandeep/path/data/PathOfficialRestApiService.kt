package ca.amandeep.path.data

import ca.amandeep.path.data.model.ResultsContainer
import ca.amandeep.path.data.model.StationName
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.Date

interface PathOfficialRestApiService {
    @GET("ridepath.json")
    suspend fun getArrivals(): ResultsContainer

    companion object {
        private const val API_PATH = "https://www.panynj.gov/bin/portauthority/"

        val INSTANCE: PathOfficialRestApiService by lazy {
            Retrofit.Builder()
                .baseUrl(API_PATH)
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder()
                            .add(StationName.Adapter())
                            .add(Date::class.java, Rfc3339DateJsonAdapter())
                            .build(),
                    ),
                )
                .build()
                .create(PathOfficialRestApiService::class.java)
        }
    }
}
