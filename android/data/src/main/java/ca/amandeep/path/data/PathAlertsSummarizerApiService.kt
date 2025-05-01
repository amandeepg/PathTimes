package ca.amandeep.path.data

import android.content.Context
import ca.amandeep.path.data.model.StationName
import ca.amandeep.path.data.model.SummarizeApiResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Date

interface PathAlertsSummarizerApiService {
    @GET("summarize")
    suspend fun summarize(
        @Query("input") input: String,
    ): SummarizeApiResponse

    companion object {
        private const val API_PATH = "https://jg6j16bqsa.execute-api.us-east-1.amazonaws.com/dev/"

        fun create(
            applicationContext: Context,
        ): PathAlertsSummarizerApiService =
            Retrofit.Builder()
                .baseUrl(API_PATH)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder()
                            .add(Date::class.java, Rfc3339DateJsonAdapter())
                            .add(StationName.Adapter())
                            .build(),
                    ),
                )
                .client(
                    OkHttpClient.Builder()
                        // 2MB cache
                        .cache(Cache(applicationContext.cacheDir, 2 * 1024 * 1024))
                        .build(),
                )
                .build()
                .create(PathAlertsSummarizerApiService::class.java)
    }
}
