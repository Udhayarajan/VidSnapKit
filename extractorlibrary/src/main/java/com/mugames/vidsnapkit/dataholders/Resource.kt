package com.mugames.vidsnapkit.dataholders

/**
 * @author Udhaya
 * Created on 16-02-2022
 */
data class VideoResource(
    val url: String,
    val mimeType: String,
    val quality: String = "--",
    var size: Long = 0,
    val hasAudio: Boolean = true
)

data class AudioResource(
    val url: String,
    val mimeType: String,
    val quality: String = "--",
    var size: Long = 0,
)
