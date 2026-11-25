package de.lagebild.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Fehler mit HTTP-Statuscode (z. B. 401 bei falschem API-Key). */
class HttpException(val code: Int, message: String) : IOException(message)

/** Abstraktion für einfache GET-Abfragen – in Tests leicht durch eine Fake-Implementierung ersetzbar. */
interface HttpFetcher {
    suspend fun getBytes(url: String): ByteArray
    suspend fun getString(url: String): String = getBytes(url).toString(Charsets.UTF_8)

    /** Besteht gerade eine Internetverbindung? (Antworten aus dem HTTP-Cache kommen auch offline.) */
    fun isOnline(): Boolean = true
}

@Singleton
class OkHttpFetcher @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
) : HttpFetcher {

    override fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun getBytes(url: String): ByteArray = runInterruptible(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, "HTTP ${response.code} für $url")
            response.body?.bytes() ?: ByteArray(0)
        }
    }
}
