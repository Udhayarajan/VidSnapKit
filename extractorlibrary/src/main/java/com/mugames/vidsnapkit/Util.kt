package com.mugames.vidsnapkit

import androidx.core.text.HtmlCompat
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.regex.Pattern
/**
 * @author Udhaya
 * Created on 22-01-2022
 */
class Util {
    companion object {
        fun decodeHTML(text: String?): String? {
            if (text == null) return null
            var decoded: String?
            decoded = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            try {
                decoded = URLDecoder.decode(decoded, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
            return decoded
        }

        fun getResolutionFromUrl(url: String): String {
            val matcher = Pattern.compile("([\\d ]{2,5}[x][\\d ]{2,5})").matcher(url)
            if (matcher.find()) return matcher.group(1) ?: "null"
            return "null"
        }

        fun filterName(name:String) = name.replace("[\n.\t]".toRegex(),"")
    }
}