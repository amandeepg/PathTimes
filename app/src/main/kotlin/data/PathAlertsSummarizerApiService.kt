package ca.amandeep.path.data

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface PathAlertsSummarizerApiService {
    @GET("summarize")
    suspend fun summarize(
        @Query("input") input: String,
    )

    companion object {
        private const val API_PATH = "https://v8qv31w7hh.execute-api.us-east-1.amazonaws.com/dev/"

        fun create(
            applicationContext: Context,
        ): PathAlertsSummarizerApiService =
            Retrofit.Builder()
                .baseUrl(API_PATH)
                .addConverterFactory(MoshiConverterFactory.create())
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
