package com.mugames.vidsnapkit.extractor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.mugames.vidsnapkit.dataholders.Error
import com.mugames.vidsnapkit.dataholders.Formats
import com.mugames.vidsnapkit.dataholders.ProgressState
import com.mugames.vidsnapkit.dataholders.Result
import com.mugames.vidsnapkit.network.HttpRequest
import kotlinx.coroutines.*
import java.util.*

/**
 * @author Udhaya
 * Created on 21-01-2022
 */
abstract class Extractor(
    context: Context,
    url:String
) {

    companion object{
        fun extract(context: Context,
                            url:String): Extractor?{
            return when {
                url.contains("facebook|fb".toRegex()) -> Facebook(context, url)
                url.contains("instagram") -> Instagram(context,url)
                else -> null
            }
        }
    }

    protected var inputUrl: String = url
    protected lateinit var onProgress: (Result) -> Unit

    protected var headers: Hashtable<String, String> = Hashtable()

    //Login user cookies
    var cookies: String? = null
    set(value) {
        value?.let {
            headers["Cookie"] = it
        }
        field = value
    }


    protected val videoFormats = mutableListOf<Formats>()

    private var context: Context = context
        get() = field.applicationContext
        set(value) {
            field = value.applicationContext
        }


    suspend fun start(progressCallback: (Result) -> Unit) {
        onProgress = progressCallback
        safeAnalyze()
    }


    private suspend fun safeAnalyze() {
        if (isNetworkAvailable()) {
            try {
                analyze()
            } catch (e: Exception) {
                onProgress(Result.Failed(Error.InternalError(e.toString())))
            }
        } else onProgress(Result.Failed(Error.NetworkError))
    }


    protected abstract suspend fun analyze()

    protected suspend fun finalize() {
        onProgress(Result.Progress(ProgressState.End))
        withContext(Dispatchers.IO) {
            for (format in videoFormats) {
                val video = async { getVideoSize(format) }
                val audio = async { getAudioSize(format) }
                val videoSize = video.await()
                val audioSize = audio.await()
                format.videoData.forEachIndexed { idx, elem ->
                    elem.size = videoSize[idx]
                }
                format.audioData.forEachIndexed { idx, elem ->
                    elem.size = audioSize[idx]
                }
            }
            onProgress(Result.Success(videoFormats))
        }
    }

    private suspend fun getVideoSize(format: Formats): List<Long> {
        val sizes = mutableListOf<Deferred<Long>>()
        coroutineScope {
            for (videoData in format.videoData) {
                sizes.add(async { HttpRequest(videoData.url).getSize() })
            }
        }
        return sizes.awaitAll()
    }

    private suspend fun getAudioSize(format: Formats): List<Long> {
        val sizes = mutableListOf<Deferred<Long>>()
        coroutineScope {
            for (audioData in format.audioData) {
                sizes.add(async { HttpRequest(audioData.url).getSize() })
            }
        }
        return sizes.awaitAll()
    }

    @SuppressWarnings("deprecation")
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) true else networkCapabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            when (networkInfo!!.type) {
                ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_ETHERNET, ConnectivityManager.TYPE_MOBILE -> true
                else -> false
            }
        }
    }
}