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
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

/**
 * @author Udhaya
 * Created on 21-01-2022
 *
 *  This is super class of all kind of Extractor
 *
 *  Don't directly create instance of [Extractor]
 *  abstract class
 *
 *  use [Extractor.findExtractor] to get required extractor
 */
abstract class Extractor(
    url: String,
) {

    companion object {
        /**
         * @param url direct URL from user input
         * @return Child class of [Extractor] based on input URL
         * and `null` if no suitable [Extractor] found
         */
        fun findExtractor(
            url: String,
        ): Extractor? {
            return when {
                url.contains("facebook|fb".toRegex()) -> Facebook(url)
                url.contains("instagram") -> Instagram(url)
                else -> null
            }
        }
    }

    protected var inputUrl: String = url
    protected var onProgress: (Result) -> Unit = { result ->

    }

    val resultFlow:MutableStateFlow<Result> = MutableStateFlow(Result.Progress(ProgressState.PreStart))

    protected var headers: Hashtable<String, String> = Hashtable()

    /**
     * If media is private just pass valid cookies to
     * extract list of [Formats]
     */
    var cookies: String? = null
        set(value) {
            value?.let {
                headers["Cookie"] = it
            }
            field = value
        }


    protected val videoFormats = mutableListOf<Formats>()


    /**
     * starting point of all child of [Extractor]
     * where net safe analyze will be called
     */
    suspend fun start(progressCallback: (Result) -> Unit) {
        onProgress = progressCallback
        safeAnalyze()
    }


    private suspend fun safeAnalyze() {
        try {
            analyze()
        } catch (e: Exception) {
            onProgress(Result.Failed(Error.InternalError("Error in SafeAnalyze", e)))
        }
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
}