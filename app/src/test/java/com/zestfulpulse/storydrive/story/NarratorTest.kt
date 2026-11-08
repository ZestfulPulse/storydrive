package com.zestfulpulse.storydrive.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarratorTest {
    private val clock = FakeScheduler()
    private val speaker = FakeSpeaker()
    private val narrator = Narrator(speaker, clock.scheduler)

    /** 엔진이 재생을 시작한 상황 (speak 직후 onStart) */
    private fun started() = narrator.onStart()

    /** 엔진이 재생을 마치고 onDone을 보낸 상황 */
    private fun done() { speaker.finished(); narrator.onDone() }

    @Test fun `재생 중에는 speak를 부르지 않고 대기열에 넣는다`() {
        narrator.enqueue("첫째")
        narrator.enqueue("둘째")
        assertEquals(listOf("첫째"), speaker.spoken)
        assertEquals(listOf("둘째"), narrator.queued)
        assertEquals(0, speaker.speakWhileSpeaking)
    }

    @Test fun `onDone을 받으면 다음 문장을 재생한다`() {
        narrator.enqueue("첫째")
        narrator.enqueue("둘째")
        started(); done()
        assertEquals(listOf("첫째", "둘째"), speaker.spoken)
        started(); done()
        assertTrue(narrator.isIdle)
    }

    @Test fun `대기열은 최대 2개, 넘치면 오래된 것부터 버린다`() {
        narrator.enqueue("재생 중")
        narrator.enqueue("A")
        narrator.enqueue("B")
        narrator.enqueue("C")
        assertEquals(listOf("B", "C"), narrator.queued)
    }

    @Test fun `예상 시간 안에 끝 신호가 없으면 stop 후 다음 문장`() {
        val text = "열 글자 문장입니다"
        narrator.enqueue(text)
        narrator.enqueue("다음")
        val expected = Narrator.expectedDurationMs(text)
        assertEquals(text.length * 375L + 2_000L, expected)

        clock.advance(expected - 1)
        assertEquals(0, speaker.stopCount)
        clock.advance(1)
        assertEquals(1, speaker.stopCount) // 타이머가 stop() 호출

        speaker.finished(); narrator.onStop() // stop()에 대한 onStop
        assertEquals(listOf(text, "다음"), speaker.spoken)
        assertEquals(0, speaker.speakWhileSpeaking)
    }

    @Test fun `onStop이 오지 않아도 대기 시간 뒤 다음 문장`() {
        narrator.enqueue("첫째")
        narrator.enqueue("둘째")
        clock.advance(Narrator.expectedDurationMs("첫째"))
        speaker.finished()
        clock.advance(1_500)
        assertEquals(listOf("첫째", "둘째"), speaker.spoken)
    }

    @Test fun `같은 발화의 onDone과 onStop이 겹쳐도 한 번만 처리한다`() {
        narrator.enqueue("첫째")
        narrator.enqueue("둘째")
        started(); done()          // 첫째 종료 → 둘째 speak (아직 onStart 전)
        narrator.onStop()          // 첫째의 늦은 onStop → 둘째를 끝내면 안 됨
        narrator.onDone()          // 첫째의 늦은 onDone도 마찬가지
        assertEquals("둘째", narrator.currentText)
        started(); done()
        assertTrue(narrator.isIdle)
        narrator.onDone()          // 다 끝난 뒤의 중복 콜백은 무시
        assertEquals(listOf("첫째", "둘째"), speaker.spoken)
    }

    @Test fun `타이머로 끝난 발화의 늦은 onDone은 무시하고, 새 발화는 계속된다`() {
        narrator.enqueue("첫째")
        narrator.enqueue("둘째")
        narrator.enqueue("셋째")
        clock.advance(Narrator.expectedDurationMs("첫째")) // 타이머 → stop()
        speaker.finished(); narrator.onStop()               // stop 완료 → 둘째 재생
        assertEquals("둘째", narrator.currentText)
        clock.advance(1_000)                                // 첫째의 옛 타이머는 취소되어 영향 없음 (둘째의 타이머보다 짧게 진행)
        assertEquals("둘째", narrator.currentText)
        assertEquals(1, speaker.stopCount)
    }

    @Test fun `stop 중에 온 늦은 onDone은 무시하고 onStop을 받은 뒤 새 문장을 재생한다`() {
        narrator.enqueue("첫째")
        started()
        narrator.interrupt("안전 안내")      // 첫째 stop() → onStop 대기
        speaker.finished()
        narrator.onDone()                   // stop 중에 온 늦은 onDone: 새 문장을 재생하지 않음
        assertEquals(listOf("첫째"), speaker.spoken)
        narrator.onStop()                   // stop 완료 → 안전 안내 재생
        assertEquals(listOf("첫째", "안전 안내"), speaker.spoken)
        narrator.onStop()                   // 안전 안내의 onStart 전 중복 onStop → 무시
        assertEquals("안전 안내", narrator.currentText)
        assertEquals(1, speaker.stopCount)
    }

    @Test fun `끊기는 대기열을 비우고 stop 완료 뒤 새 문장을 재생한다`() {
        narrator.enqueue("이야기")
        narrator.enqueue("대기 1")
        narrator.enqueue("대기 2")
        narrator.interrupt("문이 열렸어요")
        assertEquals(1, speaker.stopCount)
        assertEquals(listOf("문이 열렸어요"), narrator.queued)
        speaker.finished(); narrator.onStop()
        assertEquals(listOf("이야기", "문이 열렸어요"), speaker.spoken)
        assertEquals(0, speaker.speakWhileSpeaking)
    }

    @Test fun `speak가 실패하면 다음 문장으로 넘어간다`() {
        speaker.ready = false
        narrator.enqueue("실패")
        assertTrue(narrator.isIdle)
    }
}
