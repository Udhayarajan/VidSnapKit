package com.mugames.vidsnapkit.network

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.features.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
/**
 * @author Udhaya
 * Created on 21-01-2022
 */
class HttpRequest(
    private val url: String,
    private val headers: Hashtable<String, String>? = null,
) {
    private companion object {
        fun createClient(): HttpInterface {
            return HttpInterfaceImpl(HttpClient(Android){
                followRedirects = true
                install(Logging){
                    level = LogLevel.ALL
                }
            })
        }
    }

    suspend fun getResponse(): String = withContext(Dispatchers.IO) { createClient().getData(url, headers)}

    suspend fun getSize() = createClient().getSize(url)
}
