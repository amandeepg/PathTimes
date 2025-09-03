package ca.amandeep.path.data

import android.content.Context
import ca.amandeep.path.data.model.SummarizeApiResponse
import com.github.ajalt.timberkt.d
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Caches alert summaries to avoid repeated API calls.
 *
 * This class provides a simple file-based cache for [SummarizeApiResponse] objects.
 * Each cache entry has a time-to-live (TTL) defined by [CACHE_TTL].
 */
class AlertCache(
    private val context: Context,
) {
    private val cacheDir by lazy { File(context.cacheDir, "alert_summaries").also { it.mkdirs() } }
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(Date::class.java, Rfc3339DateJsonAdapter())
            .build()
    }
    @OptIn(ExperimentalStdlibApi::class)
    private val cacheAdapter: JsonAdapter<CachedAlertSummary> by lazy {
        moshi.adapter()
    }

    @JsonClass(generateAdapter = true)
    data class CachedAlertSummary(
        val text: String,
        val response: SummarizeApiResponse,
        val timestamp: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_TTL.inWholeMilliseconds
    }

    /**
     * Retrieves a cached [SummarizeApiResponse] for the given text.
     *
     * @param text The text of the alert to retrieve from the cache.
     * @return The cached [SummarizeApiResponse], or `null` if the entry is not found or has expired.
     */
    fun get(text: String): SummarizeApiResponse? = runCatchingIo("Error reading cache") {
        val cacheFile = getCacheFile(text)
        if (!cacheFile.exists()) return@runCatchingIo null

        val cached = cacheFile.source().buffer().use { source ->
            cacheAdapter.fromJson(source)
        }

        if (cached == null || cached.isExpired()) {
            cacheFile.delete()
            return@runCatchingIo null
        }

        cached.response
    }

    /**
     * Caches a [SummarizeApiResponse] for the given text.
     *
     * @param text The text of the alert to cache.
     * @param response The [SummarizeApiResponse] to cache.
     */
    fun put(text: String, response: SummarizeApiResponse) {
        runCatchingIo("Error writing cache") {
            val cacheFile = getCacheFile(text)
            val cached = CachedAlertSummary(text, response, System.currentTimeMillis())

            cacheFile.sink().buffer().use { sink ->
                cacheAdapter.toJson(sink, cached)
            }
        }
    }

    /**
     * Deletes all expired entries from the cache.
     */
    fun deleteExpired() {
        runCatchingIo("Error listing cache directory") {
            cacheDir.listFiles()?.forEach { file ->
                runCatchingIo("Error processing cache file ${file.name}") {
                    val cached = file.source().buffer().use { source ->
                        cacheAdapter.fromJson(source)
                    }

                    if (cached?.isExpired() == true) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun getCacheFile(text: String) = File(cacheDir, text.hashCode().toString())

    private inline fun <T> runCatchingIo(
        errorMessage: String,
        block: () -> T,
    ): T? = try {
        block()
    } catch (e: IOException) {
        d { "$errorMessage: ${e.message}" }
        null
    }

    companion object {
        private val CACHE_TTL: Duration = 5.minutes
    }
}
