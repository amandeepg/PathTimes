package ca.amandeep.path.data

import ca.amandeep.path.data.model.Stations
import ca.amandeep.path.data.model.UpcomingTrains
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.wire.GrpcClient
import okhttp3.OkHttpClient
import okhttp3.Protocol
import path_api.v1.StationsClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.Date


interface PathRazzaRestApiService {
    @GET("stations")
    suspend fun getStations(): Stations

    @GET("stations/{station}/realtime")
    suspend fun getArrivals(
        @Path("station") station: String,
    ): UpcomingTrains

    companion object {
        private const val API_PATH = "https://path.api.razza.dev/v1/"
        private const val GRPC_PATH = "http://path.grpc.razza.dev:443"

        val INSTANCE: PathRazzaRestApiService by lazy {
            Retrofit.Builder()
                .baseUrl(API_PATH)
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder()
                            .add(Date::class.java, Rfc3339DateJsonAdapter())
                            .build(),
                    ),
                )
                .build()
                .create(PathRazzaRestApiService::class.java)
        }

        val GRPC_INSTANCE: StationsClient by lazy {
            GrpcClient.Builder()
                .client(OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).retryOnConnectionFailure(true).build())
                .baseUrl(GRPC_PATH)
                .build()
                .create(StationsClient::class)
        }
    }
}
