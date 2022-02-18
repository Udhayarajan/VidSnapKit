package com.mugames.vidsnapkit

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import com.mugames.vidsnapkit.dataholders.Error
import com.mugames.vidsnapkit.dataholders.Result
import com.mugames.vidsnapkit.extractor.Extractor
import com.mugames.vidsnapkit.extractor.Facebook
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        onClick()

        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            onClick()
        }
    }

    private val callback: (Result) -> Unit = { result ->
        when (result) {
            is Result.Success -> {
                for (format in result.formats!!) {
                    Log.d(TAG, "onClick: $format")
                }
            }
            is Result.Failed -> {
                Log.e(TAG, "onClick: ${result.error}")
                when (val error = result.error) {
                    is Error.LoginInRequired -> {
                        Log.e(TAG, "Add cookies: ")
                    }
                    is Error.InternalError -> {
                        Log.e(TAG, "error = ${error.message}: ", error.e)
                    }
                    is Error.NonFatalError -> {
                        Log.e(TAG, "error = : ${error.message}")
                    }
                }
            }
            else -> {
                Log.d(TAG, "onClick: ${result.progressState}")
            }
        }
    }

    private fun onClick() {
        val url = "FACEBOOK_INSTA_URL"
        runBlocking {
            val extractor = Extractor.extract(this@MainActivity, url)
            extractor?.apply {
//                cookies = "REQUIRED_COOKIES"
                start(callback)
            }

        }
    }
}