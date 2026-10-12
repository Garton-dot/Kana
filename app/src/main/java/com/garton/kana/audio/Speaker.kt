package com.garton.kana.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Japanese text-to-speech; silently does nothing when no Japanese voice is installed. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.JAPANESE)
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setSpeechRate(0.9f)
    }

    fun speak(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }

    fun shutdown() = tts.shutdown()
}
