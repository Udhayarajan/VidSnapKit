package com.mugames.vidsnapkit.extractor

import com.mugames.vidsnapkit.*
import com.mugames.vidsnapkit.dataholders.*
import com.mugames.vidsnapkit.network.HttpRequest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern

/**
 * @author Udhaya
 * Created on 16-02-2022
 */
class Instagram internal constructor(url: String) : Extractor(url) {
    companion object {
        const val TAG: String = Statics.TAG.plus(":Instagram")
        const val STORIES_URL = "https://www.instagram.com/stories/%s/?__a=1"
        const val STORIES_API = "https://i.instagram.com/api/v1/feed/user/%s/story/"
        const val PROFILE_API = "https://www.instagram.com/%s/?__a=1"
        const val POST_API = "https://i.instagram.com/api/v1/media/%s/info/"
        const val NO_VIDEO_STATUS_AVAILABLE = "No video Status available"
        const val NO_STATUS_AVAILABLE = "No stories Available to Download"
    }

    private val formats = Formats()

    private fun getMediaId(page: String) =
        page.substringAfter("\"media_id\":\"").substringBefore("\"")

    private fun isProfileUrl(): Boolean {
        if (inputUrl.contains("/p/")) return false
        return !inputUrl.contains("(/reel/|/tv/)[\\w-]{11}".toRegex())
    }

    private fun isAccessible(jsonObject: JSONObject): Boolean {
        val user = jsonObject.getJSONObject("graphql")
            .getJSONObject("user")
        val isBlocked = user.getBoolean("has_blocked_viewer")
        val isPrivate = user.getBoolean("is_private")
        val followedByViewer = user.getBoolean("followed_by_viewer")
        return ((isPrivate && followedByViewer) || !isPrivate) && !isBlocked
    }

    private fun getUserName(): String? {
        val matcher = Pattern
            .compile("(?:https|http)://(?:www\\.|.*?)instagram.com/(?:stories/|)([A-Za-z0-9_.]+)")
            .matcher(inputUrl)
        return if (matcher.find()) matcher.group(1)
        else null
    }

    private suspend fun getUserID(): String? = cookies?.let { _ ->

        getUserName()?.let {
            val response = HttpRequest(String.format(PROFILE_API, it), headers).getResponse()
            if (!isAccessible(JSONObject(response))) {
                onProgress(Result.Failed(Error.InvalidCookies))
                return null
            }
            val page = HttpRequest(String.format(STORIES_URL, it), headers).getResponse()
            try {
                val user = JSONObject(page).getJSONObject("user")
                return user.getString("id")
            } catch (e: JSONException) {
                onProgress(Result.Failed(Error.NonFatalError(NO_STATUS_AVAILABLE)))
            }
        } ?: run {
            onProgress(Result.Failed(Error.InvalidUrl))
        }
        return null
    } ?: run {
        onProgress(Result.Failed(Error.LoginInRequired))
        null
    }

    override suspend fun analyze() {
        formats.src = "Instagram"
        formats.url = inputUrl
        if (!isProfileUrl())
            extractInfoShared(HttpRequest(inputUrl, headers).getResponse())
        else {
            val userId = getUserID()
            userId?.let {
                extractStories(it)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun extractStories(userId: String) {
        val dupHeader = getHeadersWithUserAgent()
        val stories = HttpRequest(STORIES_API.format(userId), dupHeader).getResponse()
        val reel = JSONObject(stories).getJSONObject("reel")
        extractFromItems(reel.getJSONArray("items"))
    }

    private fun getHeadersWithUserAgent(): Hashtable<String, String> {
        val dupHeader: Hashtable<String, String> = headers.clone() as Hashtable<String, String>
        dupHeader["User-Agent"] =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 12_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 105.0.0.11.118 (iPhone11,8; iOS 12_3_1; en_US; en-US; scale=2.00; 828x1792; 165586599)"
        return dupHeader
    }


    private suspend fun extractInfoShared(page: String) {
        suspend fun newApiRequest() {
            val mediaId = getMediaId(page)
            val url = POST_API.format(mediaId)
            try {
                extractFromItems(JSONObject(HttpRequest(url,
                    getHeadersWithUserAgent()).getResponse()).getJSONArray("items"))
            }catch (e: JSONException){
                onProgress(Result.Failed(Error.LoginInRequired))
            }
        }

        onProgress(Result.Progress(ProgressState.Start))
        val pattern = Pattern.compile("window\\._sharedData\\s*=\\s*(\\{.+?\\});")
        val matcher = pattern.matcher(page)
        val jsonString = if (matcher.find()) {
            matcher.group(1)
        } else {
            newApiRequest()
            return
        }
        val jsonObject = JSONObject(jsonString)
        val postPage: JSONArray? = jsonObject.getNullableJSONObject("entry_data")
            ?.getNullableJSONArray("PostPage")

        postPage?.let { post ->
            val zero: JSONObject = post.getJSONObject(0)
            val graphql: JSONObject? = zero.getNullableJSONObject("graphql")
            val media = graphql?.getNullableJSONObject("shortcode_media")
                ?: zero.getNullableJSONObject("media")
            media?.let {
                setInfo(it)
            } ?: run {
                extractInfoAdd(page)
            }
        } ?: run {
            fun isObjectPresentInEntryData(objectName: String): Boolean {
                return jsonObject.getNullableJSONObject("entry_data")
                    ?.getNullableJSONArray(objectName) != null
            }
            if (isObjectPresentInEntryData("LoginAndSignupPage")) {
                onProgress(Result.Failed(Error.LoginInRequired))
            } else if (isObjectPresentInEntryData("HttpErrorPage")) {
                onProgress(Result.Failed(Error.Instagram404Error(cookies != null)))
            } else {
                val user0 = jsonObject
                    .getJSONObject("entry_data")
                    .getJSONArray("ProfilePage")
                    .getJSONObject(0)
                if (!isAccessible(user0))
                    onProgress(Result.Failed(Error.InvalidCookies))
                else onProgress(Result.Failed(Error.InternalError("can't find problem")))
            }
        }
    }

    private suspend fun setInfo(media: JSONObject) {
        onProgress(Result.Progress(ProgressState.Middle))
        var videoName: String? = media.getNullableString("title")

        if (videoName == null || videoName == "null" || videoName.isEmpty()) videoName =
            media
                .getNullableJSONObject("edge_media_to_caption")
                ?.getNullableJSONArray("edges")
                ?.getNullableJSONObject(0)
                ?.getJSONObject("node")
                ?.getString("text")
        if (videoName == null || videoName == "null" || videoName.isEmpty()) videoName =
            "instagram_video"
        formats.title = Util.filterName(videoName)
        val fileURL: String? = media.getNullableString("video_url")
        fileURL?.let { url ->
            formats.videoData.add(VideoResource(
                url,
                MimeType.VIDEO_MP4
            ))
            formats.thumbnail.add(Pair(Util.getResolutionFromUrl(media.getString("thumbnail_src")),
                media.getString("thumbnail_src")))
            videoFormats.add(formats)
        } ?: run {
            val edges: JSONArray? = media
                .getNullableJSONObject("edge_sidecar_to_children")
                ?.getNullableJSONArray("edges")

            edges?.let { edgesObj ->
                for (i in 0 until edgesObj.length()) {
                    val format = formats.copy(videoData = mutableListOf())
                    val node = edgesObj.getJSONObject(i).getJSONObject("node")
                    if (node.getBoolean("is_video")) {
                        format.thumbnail.add(Pair(
                            Util.getResolutionFromUrl(node.getString("display_url")),
                            node.getString("display_url")
                        ))
                        format.videoData.add(VideoResource(
                            node.getString("video_url"),
                            MimeType.VIDEO_MP4
                        ))
                    }
                    videoFormats.add(format)
                }
            } ?: run {
                onProgress(Result.Failed(Error.InternalError("Media not found",
                    Exception("$media"))))
            }
        }

        finalize()
    }

    private suspend fun extractInfoAdd(page: String) {
        val pattern =
            Pattern.compile("window\\.__additionalDataLoaded\\s*\\(\\s*[^,]+,\\s*(\\{.+?\\})\\s*\\)\\s*;")
        val matcher = pattern.matcher(page)
        val jsonString = if (matcher.find()) {
            matcher.group(1)
        } else null

        if (jsonString.isNullOrEmpty()) {
            onProgress(Result.Failed(Error.LoginInRequired))
            return
        }
        val jsonObject = JSONObject(jsonString)
        val graphql: JSONObject? = jsonObject.getNullableJSONObject("graphql")
        graphql?.let {
            val media = it.getNullableJSONObject("shortcode_media")
            media?.let { mediaIt ->
                setInfo(mediaIt)
            } ?: run {
                onProgress(Result.Failed(Error.InternalError("MediaNotFound")))
            }

        } ?: run {
            extractFromItems(jsonObject.getJSONArray("items"))
        }
    }

    private suspend fun extractFromItems(items: JSONArray) {
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)

            if (formats.title.isEmpty()) {
                onProgress(Result.Progress(ProgressState.Middle))
                val caption = item.getNullableJSONObject("caption")
                formats.title = Util.filterName(
                    caption?.getNullableString("text") ?: run {
                        item.getNullableString("caption")
                            ?: "Instagram_Reels"
                    }
                )
            }


            val videoVersion = item.getNullableJSONArray("video_versions")
            videoVersion?.let {
                val format = formats.copy(videoData = mutableListOf(), thumbnail = mutableListOf())
                for (j in 0 until it.length()) {
                    val video = it.getJSONObject(j)
                    format.videoData.add(VideoResource(
                        video.getString("url"),
                        MimeType.VIDEO_MP4,
                        video.getString("width") + "x" + video.getString("height")
                    ))
                }

                val imageVersion2 = item.getJSONObject("image_versions2")
                val candidates = imageVersion2.getJSONArray("candidates")
                val thumbnailUrl = candidates.getJSONObject(0).getString("url")
                format.thumbnail.add(
                    Pair(
                        Util.getResolutionFromUrl(thumbnailUrl),
                        thumbnailUrl
                    )
                )

                videoFormats.add(format)
            } ?: run {
                item.getNullableJSONArray("carousel_media")?.let {
                    extractFromItems(it)
                    return@run
                }
            }
        }
        if (isProfileUrl() && videoFormats.isEmpty()) {
            onProgress(Result.Failed(Error.NonFatalError(NO_VIDEO_STATUS_AVAILABLE)))
        } else
            finalize()
    }
}