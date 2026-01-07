package ca.amandeep.path.data

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.OkHttpClient

internal fun OkHttpClient.Builder.addChuckerIfDebug(context: Context): OkHttpClient.Builder =
    apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(
                ChuckerInterceptor
                    .Builder(context)
                    .collector(
                        ChuckerCollector(
                            context = context,
                            showNotification = true,
                        ),
                    )
                    .redactHeaders("Authorization", "Cookie")
                    .alwaysReadResponseBody(true)
                    .build(),
            )
        }
    }
