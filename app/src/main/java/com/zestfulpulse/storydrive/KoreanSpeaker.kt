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
    engineFactory: () -> TtsEngine,
    private val callbacks: Callbacks,
    /** (android.util.Log 우선순위, 메시지). 단위 테스트에서는 android.util.Log 대신 가짜를 넣는다. */
    private val logger: (priority: Int, message: String) -> Unit = ::androidLog,
) : Speaker {
    /** 앱에서 쓰는 생성자: Pleos TTS SDK(ON_DEVICE)를 쓴다. */
    constructor(context: Context, callbacks: Callbacks) : this({ PleosTtsEngine(context) }, callbacks)

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

    private val tts = timed("TextToSpeech 생성") { engineFactory() }

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
        timed("initialize") { tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                timed("addEventListener·setLocale") {
                    tts.addEventListener(eventListener)
                    tts.setLocale(Locale.KOREA)
                }
                ready = true
                log("TTS 초기화 성공 (Pleos TTS SDK, ON_DEVICE, ko-KR)")
                callbacks.onStatus("음성 준비 완료")
            }

            override fun onFailure(errorType: ErrorType) {
                log("TTS 초기화 실패: $errorType", error = true)
                callbacks.onStatus("음성 초기화 실패: $errorType")
            }
        }) }
    }

    override fun speak(text: String): Boolean {
        if (!ready) {
            log("아직 준비되지 않아 재생하지 않음", error = true)
            return false
        }
        timed("speak") { tts.speak(text) }
        log("speak 요청: \"$text\"")
        return true
    }

    override fun stop() {
        if (!started) return
        timed("stop") { tts.stop() }
        log("stop 요청")
    }

    fun shutdown() {
        if (!started) return
        timed("shutdown") {
            tts.stop()
            tts.removeEventListener(eventListener)
            tts.release()
        }
    }

    /**
     * TTS SDK 호출 시간을 잰다. 이 클래스의 호출은 메인 스레드에서 일어나므로,
     * 오래 걸리면 화면이 멈춘다. 시험에서 확인할 수 있게 [호출 시간] 로그를 남긴다 (50ms 이상은 경고).
     */
    private inline fun <T> timed(name: String, block: () -> T): T {
        val begin = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - begin) / 1_000_000
        logger(if (ms >= SLOW_CALL_MS) Log.WARN else Log.INFO, "[호출 시간] TTS $name ${ms}ms (스레드 ${Thread.currentThread().name})")
        return result
    }

    private fun log(message: String, error: Boolean = false) = logger(if (error) Log.ERROR else Log.INFO, message)

    private companion object {
        const val SLOW_CALL_MS = 50L

        fun androidLog(priority: Int, message: String) {
            Log.println(priority, MainActivity.TAG, message)
        }
    }
}

/**
 * Pleos TTS SDK 중 이 앱이 쓰는 호출만 모은 것. 단위 테스트에서는 가짜 엔진으로 바꾼다.
 * SDK는 initialize() 전에 stop()/release() 등을 부르면 UninitializedPropertyAccessException을 던진다.
 */
interface TtsEngine {
    fun initialize(listener: OnInitializeListener)
    fun addEventListener(listener: EventListener)
    fun removeEventListener(listener: EventListener)
    fun setLocale(locale: Locale)
    fun speak(text: String)
    fun stop()
    fun release()
}

/** 실제 Pleos TextToSpeech SDK (ON_DEVICE 모드만). */
class PleosTtsEngine(context: Context) : TtsEngine {
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    override fun initialize(listener: OnInitializeListener) = tts.initialize(listener)
    override fun addEventListener(listener: EventListener) = tts.addEventListener(listener)
    override fun removeEventListener(listener: EventListener) = tts.removeEventListener(listener)
    override fun setLocale(locale: Locale) = tts.setLocale(locale)
    override fun speak(text: String) = tts.speak(text)
    override fun stop() = tts.stop()
    override fun release() = tts.release()
}
