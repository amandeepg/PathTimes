package ca.amandeep.path.data;

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit

class UnixTimestampDateJsonAdapter : JsonAdapter<Date>() {
    @Synchronized
    @Throws(IOException::class)
    override fun fromJson(reader: JsonReader): Date =
        Date(TimeUnit.SECONDS.toMillis(reader.nextLong()))

    @Synchronized
    @Throws(IOException::class)
    override fun toJson(writer: JsonWriter, value: Date?) {
        writer.value(value?.time?.let(TimeUnit.MILLISECONDS::toSeconds))
    }
}