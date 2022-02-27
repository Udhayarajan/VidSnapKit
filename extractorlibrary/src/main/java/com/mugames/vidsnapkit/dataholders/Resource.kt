package com.mugames.vidsnapkit.dataholders

/**
 * @author Udhaya
 * Created on 16-02-2022
 */

/**
 * Used to store basic details of Video Available for given link
 *
 * @param url Direct URL for the particular video. It will be downloadable
 * @param mimeType Mimetype of the video
 * @param quality Resolution of the video. Eg:1080x1920, Default Value: `--`
 * @param size Size of video in bytes
 * @param hasAudio Specifies does this video has Audio with it.
 */
data class VideoResource(
    val url: String,
    val mimeType: String,
    val quality: String = "--",
    var size: Long = 0,
    val hasAudio: Boolean = true
)

/**
 * Used to store basic details of Audio Available for given link
 *
 * @param url Direct URL for the particular video. It will be downloadable
 * @param mimeType Mimetype of the video
 * @param size Size of video in bytes
 */
data class AudioResource(
    val url: String,
    val mimeType: String,
    var size: Long = 0,
)
