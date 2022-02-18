package com.mugames.vidsnapkit.network

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.util.*

/**
 * @author Udhaya
 * Created on 21-01-2022
 */
interface HttpInterface {
    suspend fun getData(url: String, headers: Hashtable<String, String>? = null): String
    suspend fun getSize(url: String, headers: Hashtable<String, String>? = null): Long
}

class HttpInterfaceImpl(
    private val client: HttpClient,
) : HttpInterface {
    override suspend fun getData(url: String, headers: Hashtable<String, String>?): String {
        return try {
            client.get {
                url(url)
                headers?.let {
                    if (it.isNotEmpty()) {
                        headers {
                            for ((key, value) in it)
                                append(key, value)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getSize(url: String, headers: Hashtable<String, String>?): Long {
        val request = client.request<HttpResponse>(url) {
            method = HttpMethod.Head
        }
        return request.headers["content-length"]?.toLong() ?: Long.MIN_VALUE
    }

}