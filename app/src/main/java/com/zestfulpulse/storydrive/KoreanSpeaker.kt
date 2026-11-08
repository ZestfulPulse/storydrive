package com.zestfulpulse.storydrive

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * Pleos TextToSpeech SDK를 ON_DEVICE 모드로만 사용하는 한국어 음성 합성 래퍼.
 * - 서버/HYBRID 모드, 앱 등록(Client ID/Secret)은 쓰지 않는다.
 * - 상태 변화는 [onStatus]로 화면에 알리고, 같은 내용을 logcat(TAG=StoryDrive)에 남긴다.
 * 표준 android.speech.tts.TextToSpeech는 Pleos 에뮬레이터에서 엔진을 찾지 못해 사용하지 않는다.
 */
class KoreanSpeaker(
    context: Context,
    private val onStatus: (String) -> Unit,
) {
    private var ready = false

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    private val eventListener = object : EventListener {
        override fun onReady() = report("엔진 준비됨 (onReady)")
        override fun onStart() = report("재생 시작 (onStart)")
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = report("재생 완료 (onDone)")
        override fun onStop() = report("재생 중지 (onStop)")
        override fun onError(errorType: ErrorType) = report("재생 오류: $errorType", error = true)
    }

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(eventListener)
                tts.setLocale(Locale.KOREA)
                ready = true
                report("TTS 초기화 성공 (Pleos TTS SDK, ON_DEVICE, ko-KR)")
            }

            override fun onFailure(errorType: ErrorType) {
                report("TTS 초기화 실패: $errorType", error = true)
            }
        })
    }

    fun speak(text: String) {
        if (!ready) {
            report("아직 준비되지 않아 재생하지 않음", error = true)
            return
        }
        tts.speak(text)
        report("speak 요청: \"$text\"")
    }

    fun shutdown() {
        tts.stop()
        tts.removeEventListener(eventListener)
        tts.release()
    }

    private fun report(message: String, error: Boolean = false) {
        if (error) Log.e(MainActivity.TAG, message) else Log.i(MainActivity.TAG, message)
        onStatus(message)
    }
}
