package com.mugames.vidsnapkit.dataholders

/**
 * @author Udhaya
 * Created on 21-01-2022
 *
 * For a single video it's properties
 * are listed in this class
 */
data class Formats(
    var title: String = "",
    var url:String = "",
    var src:String = "",

    val thumbnail: MutableMap<String, String> = mutableMapOf(),

    val videoData: MutableList<VideoResource> = mutableListOf(),

    val audioData: MutableList<AudioResource> = mutableListOf(),

    //Flag to keep remember what index is selected
    var selectedVideo: Int? = null,
    var selectedAudio: Int? = null,
    var selectedThumbnailRes: String? = null,
)

fun Formats.getSelectedVideo(): VideoResource = videoData[selectedVideo ?: 0]

fun Formats.getSelectedAudio(): AudioResource = audioData[selectedAudio ?: 0]

fun Formats.getSelectedThumbnailUrl(): String = thumbnail[selectedThumbnailRes]?:""

