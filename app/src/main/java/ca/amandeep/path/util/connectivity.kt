package ca.amandeep.path.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

@ExperimentalCoroutinesApi
fun Context.observeConnectivity() = callbackFlow {
    val connectivityManager = getSystemService<ConnectivityManager>()

    val callback = networkCallback { connectionState -> trySend(connectionState) }

    val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    connectivityManager?.registerNetworkCallback(networkRequest, callback)
    connectivityManager?.getCurrentConnectivityState()?.let(this::trySend)

    awaitClose {
        connectivityManager?.unregisterNetworkCallback(callback)
    }
}

private fun networkCallback(callback: (ConnectionState) -> Unit): ConnectivityManager.NetworkCallback {
    return object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            callback(ConnectionState.Available)
        }

        override fun onLost(network: Network) {
            callback(ConnectionState.Unavailable)
        }
    }
}

sealed class ConnectionState {
    object Available : ConnectionState()
    object Unavailable : ConnectionState()
}

val Context.currentConnectivityState: ConnectionState
    get() = getSystemService<ConnectivityManager>()?.getCurrentConnectivityState()
        ?: ConnectionState.Unavailable

private fun ConnectivityManager.getCurrentConnectivityState(): ConnectionState =
    getNetworkCapabilities(activeNetwork)
        ?.let { actNetwork ->
            when {
                actNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    ConnectionState.Available
                actNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    ConnectionState.Available
                else ->
                    ConnectionState.Unavailable
            }
        } ?: ConnectionState.Unavailable