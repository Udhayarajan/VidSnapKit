package com.mugames.vidsnapkit.extractor

import android.content.Context
import android.util.Log
import com.mugames.vidsnapkit.*
import com.mugames.vidsnapkit.Util.Companion.decodeHTML
import com.mugames.vidsnapkit.dataholders.*
import com.mugames.vidsnapkit.network.HttpRequest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.XML
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @author Udhaya
 * Created on 21-01-2022
 */
class Facebook internal constructor(url: String) : Extractor(url) {
    private var triedWithForceEng = false

    private val formats = Formats()


    private var userAgent = """
        Mozilla/5.0 (Windows NT 10.0; Win64; x64) 
        AppleWebKit/537.36 (KHTML, like Gecko) 
        Chrome/69.0.3497.122 Safari/537.36
    """.trimIndent().replace("\n", "")

    override suspend fun analyze() {
        formats.url = inputUrl
        formats.src = "FaceBook"
        fun findVideoId(): String? {
            var pattern =
                Pattern.compile("(?:https?://(?:[\\w-]+\\.)?(?:facebook\\.com|facebookcorewwwi\\.onion)/(?:[^#]*?#!/)?(?:(?:video/video\\.php|photo\\.php|video\\.php|video/embed|story\\.php|watch(?:/live)?/?)\\?(?:.*?)(?:v|video_id|story_fbid)=|[^/]+/videos/(?:[^/]+/)?|[^/]+/posts/|groups/[^/]+/permalink/|watchparty/)|facebook:)([0-9]+)")
            var matcher = pattern.matcher(inputUrl)
            return when {
                matcher.find() -> matcher.group(1)
                inputUrl.contains("fb.") -> {
                    if (!inputUrl.endsWith("/")) inputUrl = inputUrl.plus("/")
                    pattern = Pattern.compile("https?://fb\\.watch/(.*?)/")
                    matcher = pattern.matcher(inputUrl)
                    if (matcher.find()) matcher.group(1) else null
                }
                else -> null
            }
        }
        headers["Accept"] =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        headers["User-Agent"] = userAgent
        if (inputUrl.startsWith("facebook:")) inputUrl =
            "https://www.facebook.com/video/video.php?v=${findVideoId()}"
        try {
            onProgress(Result.Progress(ProgressState.Start))
            extractInfo()
        } catch (e: JSONException) {
            e.printStackTrace()
            onProgress(Result.Failed(Error.InternalError("Something went wrong", e)))
        } catch (e: Exception) {
            Log.e(TAG, "analyze: ", e)
            throw e
        }
    }

    private suspend fun extractInfo() {
        inputUrl = inputUrl.replace("://m.facebook\\.com/".toRegex(), "://www.facebook.com/")
        scratchWebPage(HttpRequest(inputUrl, headers).getResponse())
    }

    private suspend fun extractForceEng() {
        inputUrl = inputUrl.replace("://m.facebook\\.com/".toRegex(), "://www.facebook.com/")
        inputUrl = inputUrl.replace("://www.facebook\\.com/".toRegex(), "://en-gb.facebook.com/")
        triedWithForceEng = true
        headers["Accept-Language"] = "en-GB,en-US,en"
        scratchWebPage(HttpRequest(inputUrl, headers).getResponse())
    }

    private suspend fun scratchWebPage(webPage: String) {
        onProgress(Result.Progress(ProgressState.Middle))
        var serverJsData: String? = null
        var matcher =
            Pattern.compile("handleServerJS\\((\\{.+\\})(?:\\);|,\")").matcher(webPage)
        if (matcher.find()) serverJsData = matcher.group(1) else {
            matcher = Pattern.compile("\\bs\\.handle\\((\\{.+?\\})\\);").matcher(webPage)
            if (matcher.find()) serverJsData = matcher.group(1)
        }
        var videoData: Any? = null
        if (!serverJsData.isNullOrBlank()) {
            videoData = grabVideoData(JSONObject(serverJsData).getJSONArray("instances"))
        }
        if (videoData == null) {
            matcher =
                Pattern.compile("bigPipe\\.onPageletArrive\\((\\{.+?\\})\\)\\s*;\\s*\\}\\s*\\)\\s*,\\s*[\"']onPageletArrive\\s+$PAGELET_REGEX")
                    .matcher(webPage)
            if (matcher.find()) serverJsData = matcher.group(1) else {
                matcher =
                    Pattern.compile(String.format("bigPipe\\.onPageletArrive\\((\\{.*?id\\s*:\\s*\\\"%s\\\".*?\\})\\);",
                        PAGELET_REGEX)).matcher(webPage)
                if (matcher.find()) serverJsData = matcher.group(1)
            }
            if (!serverJsData.isNullOrBlank()) videoData =
                grabFromJSModsInstance(JSONObject(serverJsData))
        }
        if (videoData == null) {
            videoData = grabRelayPrefetchedDataSearchUrl(webPage)
        }
        if (videoData == null) {
            matcher =
                Pattern.compile("class=\"[^\"]*uiInterstitialContent[^\"]*\"><div>(.*?)</div>")
                    .matcher(webPage)
            if (matcher.find()) {
                onProgress(Result.Failed(Error.InternalError(

                    "This video unavailable. FB says : " + matcher.group(
                        1)
                )))
                return
            }
            if (webPage.contains("You must log in to continue")) {
                onProgress(Result.Failed(Error.LoginInRequired))
                return
            }
        }
        videoData?.let {
            var m: Matcher
            fun extractThumbnail(vararg regexes: Regex) {
                for (regex in regexes) {
                    m = Pattern.compile(regex.toString()).matcher(webPage)
                    if (m.find()) {
                        formats.thumbnail.add(Pair(Util.getResolutionFromUrl(m.group(1)!!),
                            m.group(1)!!))
                        return
                    }
                }
            }

            fun extractTitle(vararg regexes: Regex, default: String = "") {
                for (regex in regexes) {
                    m = Pattern.compile(regex.toString())
                        .matcher(webPage)
                    if (m.find()) {
                        formats.title = decodeHTML(m.group(1)!!).toString()
                        return
                    }
                    formats.title = default
                }
            }

            if (formats.thumbnail.isEmpty()) extractThumbnail(
                Regex("\"thumbnailImage\":\\{\"uri\":\"(.*?)\"\\}"),
                Regex("\"thumbnailUrl\":\"(.*?)\""),
                Regex("\"twitter:image\"\\s*?content\\s*?=\\s*?\"(.*?)\"")
            )

            if (formats.title.isEmpty() || formats.title == "null") {
                extractTitle(
                    Regex("(?:true|false),\"name\":\"(.*?)\",\"savable"),
                    Regex("<[Tt]itle id=\"pageTitle\">(.*?) \\| Facebook<\\/title>"),
                    Regex("title\" content=\"(.*?)\""),
                    default = "Facebook_Video"
                )
            }
            videoFormats.add(formats)
            finalize()
        } ?: apply {
            if (!triedWithForceEng) extractForceEng() else onProgress(
                Result.Failed(Error.InternalError("This video can't be Downloaded"))
            )
        }
    }


    private fun grabRelayPrefetchedDataSearchUrl(webpage: String): Any? {
        fun parseAttachment(attachment: JSONObject?, key: String) {
            val media = attachment?.getNullableJSONObject(key)
            media?.let {
                if (it.getString("__typename") == "Video") {
                    parseGraphqlVideo(it)
                }
            }
        }

        val data =
            grabRelayPrefetchedData(webpage, arrayOf("\"dash_manifest\"", "\"playable_url\""))
        data?.let {
            var nodes = it.getNullableJSONArray("nodes")
            var node = it.getNullableJSONObject("node")

            if (nodes == null && node != null) {
                nodes = JSONArray().apply {
                    put(data)
                }
            }

            nodes?.let { nodesIt ->
                for (i in 0 until nodesIt.length()) {
                    node = nodesIt.getNullableJSONObject(i)?.getNullableJSONObject("node")

                    val story = node!!.getJSONObject("comet_sections")
                        .getJSONObject("content")
                        .getJSONObject("story")

                    val attachments = story.getNullableJSONObject("attached_story")
                        ?.getNullableJSONArray("attachments")
                        ?: story.getJSONArray("attachments")

                    for (j in 0 until attachments.length()) {
                        //attachments.getJSONObject(j).getJSONObject("style_type_renderer").getJSONObject("attachment");
                        val attachment = attachments.getNullableJSONObject(j)
                            ?.getNullableJSONObject("style_type_renderer")
                            ?.getNullableJSONObject("attachment")

                        val ns = attachment?.getNullableJSONObject("all_subattachments")
                            ?.getNullableJSONArray("nodes")

                        ns?.let { nsIt ->
                            for (l in 0 until nsIt.length()) {
                                parseAttachment(nsIt.getJSONObject(l), "media")
                            }
                        }
                        parseAttachment(attachment, "media")

                    }
                }
            }

            val edges = it.getNullableJSONObject("mediaset")
                ?.getNullableJSONObject("currMedia")
                ?.getNullableJSONArray("edges")

            if (edges != null) {
                for (j in 0 until edges.length()) {
                    val edge = edges.getJSONObject(j)
                    parseAttachment(edge, "node")
                }
            }

            val video = it.getNullableJSONObject("video")

            if (video != null) {
                val attachments: JSONArray? = video.getNullableJSONObject("story")
                    ?.getNullableJSONArray("attachments")
                    ?: video.getNullableJSONObject("creation_story")
                        ?.getNullableJSONArray("attachments")


                if (attachments != null) {
                    for (j in 0 until attachments.length()) {
                        parseAttachment(attachments.getJSONObject(j), "media")
                    }
                }
                if (formats.videoData.isEmpty()) parseGraphqlVideo(video)
            }
            if (formats.videoData.isNotEmpty()) return SUCCESS
        }
        return null
    }

    private fun grabRelayData(webPage: String, searchWords: Array<String>): String? {
        val m = Pattern.compile("handleWithCustomApplyEach\\(.*?,(.*)\\);").matcher(webPage)
        while (m.find()) {
            val m1 =
                Pattern.compile("(\\{.*[^);]\\})\\);").matcher(Objects.requireNonNull(m.group(1)))
            if (m1.find())
                for (s in searchWords) {
                    val temp = m1.group(1)!!
                    if (temp.contains(s))
                        return m1.group(1)
                }
        }
        return null
    }

    private fun grabRelayPrefetchedData(webPage: String, filter: Array<String>): JSONObject? {
        val jsonString = grabRelayData(webPage, filter)

        if (!jsonString.isNullOrBlank()) {
            val require = JSONObject(jsonString).getJSONArray("require")
            for (i in 0 until require.length()) {
                val array = require.getJSONArray(i)
                if (array.getString(0) == "RelayPrefetchedStreamCache") return array.getJSONArray(
                    3).getJSONObject(1).getJSONObject("__bbox").getJSONObject("result")
                    .getJSONObject("data")
            }
        }
        return null
    }

    private fun parseGraphqlVideo(media: JSONObject) {
        val thumbnailUrl = media.getNullableJSONObject("thumbnailImage")?.getString("uri")
            ?: media.getJSONObject("preferred_thumbnail")
                .getJSONObject("image")
                .getString("uri")
        val thumbnailRes = Util.getResolutionFromUrl(thumbnailUrl)
        formats.thumbnail.add(Pair(thumbnailRes, thumbnailUrl))
        formats.title = media.getNullableString("name")
            ?: media.getNullableJSONObject("savable_description")
                ?.getNullableString("text")
                    ?: "FaceBook_Video"

        val dashXml = media.getNullableString("dash_manifest")
        dashXml?.let {
            extractFromDash(it)
        }

        val res: String = media.getNullableString("width")
            ?: (media["original_width"].toString() + "x" + (media.getNullableString("height")
                ?: media["original_height"].toString()))

        for (suffix in arrayOf("", "_quality_hd")) {
            val playableUrl = media.getNullableString("playable_url$suffix")
            if (playableUrl == null || playableUrl == "null") continue
            formats.videoData.add(VideoResource(
                playableUrl,
                MimeType.VIDEO_MP4,
                if (suffix == "") "$res(SD)" else "$res(HD)"
            ))

        }
    }

    private fun extractFromDash(xml: String) {
        var xmlDecoded = xml.replace("x3C".toRegex(), "<")
        xmlDecoded = xmlDecoded.replace("\\\\\u003C".toRegex(), "<")
        val adaptionSet: JSONArray =
            XML.toJSONObject(xmlDecoded).getJSONObject("MPD").getJSONObject("Period")
                .getJSONArray("AdaptationSet")
        val videos = adaptionSet.getJSONObject(0).getJSONArray("Representation")
        val audios = adaptionSet.getJSONObject(1)
        val audioUrl: String
        var audioMime: String?
        var res: String
        var pre = ""
        val audioRep = try {
            audios.getJSONObject("Representation")
        } catch (e: JSONException) {
            audios.getJSONArray("Representation").getJSONObject(0)
        }
        audioUrl = audioRep.getString("BaseURL")
        audioMime = audioRep.getNullableString("_mimeType")
        if (audioMime == null) audioMime = audioRep.getString("mimeType") else pre = "_"
        for (i in 0 until videos.length()) {
            val video = videos.getJSONObject(i)
            val videoUrl = video.getString("BaseURL")
            res = try {
                video.getString(pre + "FBQualityLabel") + "(" + video.getString(pre + "FBQualityClass")
                    .uppercase() + ")"
            } catch (e: JSONException) {
                video[pre + "width"].toString() + "x" + video[pre + "height"]
            }
            val videoMime = video.getString(pre + "mimeType")
            formats.videoData.add(VideoResource(
                videoUrl,
                videoMime,
                res,
                hasAudio = false
            ))
            val audio = audioMime?.let {
                AudioResource(audioUrl, it)
            } ?: AudioResource(audioUrl, MimeType.AUDIO_MP4)
            formats.audioData.add(audio)
        }
    }

    private fun grabFromJSModsInstance(jsData: JSONObject): Any? {
        if (jsData.toString().isNotBlank()) {
            return grabVideoData(jsData.getJSONObject("jsmods").getJSONArray("instances"))
        }
        return null
    }

    private fun grabVideoData(instance: JSONArray): Any? {
        for (i in 0 until instance.length()) {
            val item = instance.getJSONArray(i)
            if (item.getJSONArray(1).getString(0) == "VideoConfig") {
                val videoDetails = item.getJSONArray(2).getJSONObject(0)
                val videoData = videoDetails.getJSONArray("videoData").getJSONObject(0)
                val dashXml = videoData.getNullableString("dash_manifest")
                dashXml?.let { extractFromDash(it) }

                for (s in arrayOf("hd", "sd")) {
                    val url = videoData.getNullableString("${s}_src")
                    if (url == null || url == "null") continue
                    formats.videoData.add(VideoResource(
                        url,
                        MimeType.VIDEO_MP4,
                        videoData.getString("original_width") + "x" +
                                videoData.getString("original_height") + "(" +
                                s.uppercase() + ")",

                        ))
                }
                return SUCCESS
            }
        }
        return null
    }


    companion object {
        const val TAG: String = Statics.TAG.plus(":Facebook")
        const val SUCCESS = -1 //Null if fails
        var PAGELET_REGEX =
            "(?:pagelet_group_mall|permalink_video_pagelet|hyperfeed_story_id_[0-9a-f]+)".toRegex()
    }
}