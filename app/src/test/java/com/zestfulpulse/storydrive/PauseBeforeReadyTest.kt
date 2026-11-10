package com.zestfulpulse.storydrive

import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.zestfulpulse.storydrive.story.DetectedEvent
import com.zestfulpulse.storydrive.story.FakeScheduler
import com.zestfulpulse.storydrive.story.Narrator
import com.zestfulpulse.storydrive.story.SceneBook
import com.zestfulpulse.storydrive.story.StoryDirector
import com.zestfulpulse.storydrive.story.StoryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/**
 * 릴리스 기준 3번(2026-11-10 사용자 결정): "TTS·차량 SDK 준비 전에 '이야기 잠시 멈추기'를 눌러도 안전하다"를
 * 에뮬레이터 대신 단위 테스트로 검증한다.
 *
 * 가짜 엔진은 실제 Pleos TTS SDK 3.3.1에서 확인한 동작을 흉내 낸다.
 * - initialize() 전에 stop()/release() 등을 부르면 UninitializedPropertyAccessException
 *   (`lateinit property tts has not been initialized`, f6589d7에서 고친 크래시)
 * - initialize()가 불리면 SDK 내부 객체는 바로 만들어지고, 준비 완료(onSuccess)는 나중에 온다
 *   (에뮬레이터 로그: initialize → createTts가 같은 호출 안에서, onServiceConnected·onSuccess는 나중에)
 * 차량 SDK는 "멈추기" 경로에서 쓰이지 않는다. 차량 신호가 하나도 오지 않은 상태를 함께 검증한다.
 */
class PauseBeforeReadyTest {
    private class FakeEngine : TtsEngine {
        var initialized = false
            private set
        private var initListener: OnInitializeListener? = null
        val calls = mutableListOf<String>()
        val spoken = mutableListOf<String>()

        private fun sdkCall(name: String) {
            if (!initialized) throw UninitializedPropertyAccessException("lateinit property tts has not been initialized")
            calls += name
        }

        override fun initialize(listener: OnInitializeListener) {
            initialized = true
            initListener = listener
            calls += "initialize"
        }
        override fun addEventListener(listener: EventListener) = sdkCall("addEventListener")
        override fun removeEventListener(listener: EventListener) = sdkCall("removeEventListener")
        override fun setLocale(locale: Locale) = sdkCall("setLocale")
        override fun speak(text: String) { sdkCall("speak"); spoken += text }
        override fun stop() = sdkCall("stop")
        override fun release() = sdkCall("release")

        /** 음성 서비스 연결이 끝나 준비 완료 콜백이 온 상황 */
        fun becomeReady() = initListener!!.onSuccess()
    }

    private val engine = FakeEngine()
    private val statuses = mutableListOf<String>()
    private val speaker = KoreanSpeaker(
        engineFactory = { engine },
        callbacks = object : KoreanSpeaker.Callbacks {
            override fun onStatus(message: String) { statuses += message }
            override fun onStart() = Unit
            override fun onDone() = Unit
            override fun onStop() = Unit
            override fun onError() = Unit
        },
        logger = { _, _ -> },
    )
    private val clock = FakeScheduler()
    private val narrator = Narrator(speaker, clock.scheduler)
    private val director = StoryDirector(
        SceneBook(StoryEvent.entries.associateWith { listOf("${it.name} 장면") }, random = Random(1)),
        narrator,
        clock.scheduler,
        now = { clock.now },
    )

    /** MainActivity의 "이야기 잠시 멈추기" 버튼과 같은 호출 */
    private fun pressPause() = director.stopStory()

    /** MainActivity의 "이야기 시작" 버튼과 같은 호출 */
    private fun pressStart() = director.handle(DetectedEvent(StoryEvent.START))

    @Test fun `SDK 시작 전(첫 화면 직후) 멈추기를 눌러도 예외가 없고 SDK를 부르지 않는다`() {
        repeat(3) { pressPause() }
        speaker.stop()
        assertTrue(engine.calls.isEmpty())
        assertTrue(director.stoppedByUser)
    }

    @Test fun `SDK 시작 전에 화면이 닫혀도 종료 처리가 SDK를 부르지 않는다`() {
        pressPause()
        speaker.shutdown()
        assertTrue(engine.calls.isEmpty())
    }

    @Test fun `SDK 시작 후 TTS 준비 전 멈추기를 눌러도 안전하고 재생 요청은 거절된다`() {
        speaker.start()
        repeat(3) { pressPause() }
        speaker.stop() // 준비 전 SDK stop()도 예외 없이 지나가야 한다
        assertFalse(speaker.speak("준비 전 문장"))
        assertTrue(engine.spoken.isEmpty())
        assertEquals(listOf("initialize", "stop"), engine.calls)
    }

    @Test fun `준비 전 차량 신호와 멈추기가 섞여도 예외가 없고, 준비 뒤 시작하면 정상 재생된다`() {
        // 차량 신호가 TTS보다 먼저 와도 재생은 거절되고 상태는 대기로 돌아온다
        assertTrue(director.handle(DetectedEvent(StoryEvent.TURN_LEFT)))
        assertTrue(narrator.isIdle)
        pressPause()
        speaker.start()
        pressPause()
        // 멈춘 동안 온 차량 신호는 무시
        assertFalse(director.handle(DetectedEvent(StoryEvent.STOP)))
        engine.becomeReady()
        assertEquals("음성 준비 완료", statuses.last())

        assertTrue(pressStart())
        assertEquals(listOf("START 장면"), engine.spoken)

        // 재생 중 멈추기는 SDK stop()으로 이어진다
        pressPause()
        assertEquals("stop", engine.calls.last())
        assertTrue(director.stoppedByUser)
    }

    @Test fun `준비 전에 화면이 닫혀도 종료 처리에서 예외가 없다`() {
        speaker.start()
        pressPause()
        speaker.shutdown()
        assertEquals(listOf("initialize", "stop", "removeEventListener", "release"), engine.calls)
    }
}
