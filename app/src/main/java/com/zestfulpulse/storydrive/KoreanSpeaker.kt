package com.zestfulpulse.storydrive

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import com.zestfulpulse.storydrive.story.Speaker
import java.util.Locale

/**
 * Pleos TextToSpeech SDK를 ON_DEVICE 모드로만 사용하는 한국어 음성 합성 래퍼.
 * - 서버/HYBRID 모드, 앱 등록(Client ID/Secret)은 쓰지 않는다.
 * - 재생 순서 관리(대기열, 타이머, 중복 콜백 처리)는 [com.zestfulpulse.storydrive.story.Narrator]가 한다.
 *   이 클래스는 SDK 호출과 콜백 전달만 한다. 콜백은 SDK 스레드에서 오므로 받는 쪽에서 메인 스레드로 넘긴다.
 * 표준 android.speech.tts.TextToSpeech는 Pleos 에뮬레이터에서 엔진을 찾지 못해 사용하지 않는다.
 */
class KoreanSpeaker(
    context: Context,
    private val callbacks: Callbacks,
) : Speaker {
    interface Callbacks {
        fun onStatus(message: String)
        fun onStart()
        fun onDone()
        fun onStop()
        fun onError()
    }

    @Volatile private var ready = false

    /** SDK는 initialize() 전에 stop()/release()를 부르면 예외를 던진다 (화면 재생성으로 start 전에 onDestroy가 오는 경우). */
    private var started = false

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    private val eventListener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() { log("재생 시작 (onStart)"); callbacks.onStart() }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { log("재생 완료 (onDone)"); callbacks.onDone() }
        override fun onStop() { log("재생 중지 (onStop)"); callbacks.onStop() }
        override fun onError(errorType: ErrorType) { log("재생 오류: $errorType", error = true); callbacks.onError() }
    }

    /** SDK 초기화를 시작한다. 첫 화면을 그린 뒤 호출한다 (앱 실행 시간 단축). */
    fun start() {
        if (started) return
        started = true
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(eventListener)
                tts.setLocale(Locale.KOREA)
                ready = true
                log("TTS 초기화 성공 (Pleos TTS SDK, ON_DEVICE, ko-KR)")
                callbacks.onStatus("음성 준비 완료")
            }

            override fun onFailure(errorType: ErrorType) {
                log("TTS 초기화 실패: $errorType", error = true)
                callbacks.onStatus("음성 초기화 실패: $errorType")
            }
        })
    }

    override fun speak(text: String): Boolean {
        if (!ready) {
            log("아직 준비되지 않아 재생하지 않음", error = true)
            return false
        }
        tts.speak(text)
        log("speak 요청: \"$text\"")
        return true
    }

    override fun stop() {
        if (!started) return
        tts.stop()
        log("stop 요청")
    }

    fun shutdown() {
        if (!started) return
        tts.stop()
        tts.removeEventListener(eventListener)
        tts.release()
    }

    private fun log(message: String, error: Boolean = false) {
        if (error) Log.e(MainActivity.TAG, message) else Log.i(MainActivity.TAG, message)
    }
}
