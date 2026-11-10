package com.zestfulpulse.storydrive.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StoryDirectorTest {
    private val clock = FakeScheduler()
    private val speaker = FakeSpeaker()
    private val narrator = Narrator(speaker, clock.scheduler)
    private val scenes = SceneBook(
        StoryEvent.entries.associateWith { listOf("${it.name} 장면") } +
            (StoryEvent.START to listOf("안녕, {아이1}, {아이2}!")),
        random = Random(1),
    )
    private val director = StoryDirector(scenes, narrator, clock.scheduler, now = { clock.now })

    private fun send(event: StoryEvent, rear: Boolean = false) = director.handle(DetectedEvent(event, rearSeat = rear))

    /** 지금 재생 중인 문장을 끝까지 들은 상황 */
    private fun finishCurrent() {
        narrator.onStart(); speaker.finished(); narrator.onDone()
    }

    @Test fun `장면 문장의 이름 자리는 대장, 부대장으로 채운다`() {
        send(StoryEvent.START)
        assertEquals("안녕, 대장, 부대장!", speaker.spoken.single())
    }

    @Test fun `같은 사건은 8초 안에 다시 오면 무시한다`() {
        assertTrue(send(StoryEvent.TURN_LEFT))
        clock.advance(7_999)
        assertFalse(send(StoryEvent.TURN_LEFT))
        clock.advance(1)
        assertTrue(send(StoryEvent.TURN_LEFT))
        assertTrue(send(StoryEvent.TURN_RIGHT)) // 다른 사건은 상관없음
    }

    @Test fun `문 열림은 지금 문장을 끊고 대기열을 비운 뒤 안내를 먼저 재생한다`() {
        send(StoryEvent.STOP)
        send(StoryEvent.TURN_LEFT)
        send(StoryEvent.RAIN_ON)
        assertTrue(send(StoryEvent.DOOR_OPEN))
        assertEquals(1, speaker.stopCount)
        assertEquals(listOf("DOOR_OPEN 장면"), narrator.queued)
        speaker.finished(); narrator.onStop()
        assertEquals(listOf("STOP 장면", "DOOR_OPEN 장면"), speaker.spoken)
    }

    @Test fun `벨트 풀림도 안전 사건으로 즉시 끊는다`() {
        send(StoryEvent.STOP)
        send(StoryEvent.BELT_WARN)
        assertEquals(1, speaker.stopCount)
        assertTrue(director.paused)
    }

    @Test fun `멈춤 중에는 일반 사건을 재생하지 않고, RESUME 뒤에 다시 받는다`() {
        send(StoryEvent.DOOR_OPEN)
        finishCurrent()
        assertFalse(send(StoryEvent.TURN_LEFT))
        assertTrue(send(StoryEvent.RESUME))
        assertFalse(director.paused)
        finishCurrent()
        assertTrue(send(StoryEvent.TURN_LEFT))
        assertEquals(listOf("DOOR_OPEN 장면", "RESUME 장면", "TURN_LEFT 장면"), speaker.spoken)
    }

    @Test fun `안전 사건과 RESUME은 8초 중복 무시를 적용하지 않는다`() {
        assertTrue(send(StoryEvent.DOOR_OPEN))
        assertTrue(send(StoryEvent.RESUME))
        assertTrue(send(StoryEvent.DOOR_OPEN))
    }

    @Test fun `멈춤이 아닐 때 RESUME은 무시한다`() {
        assertFalse(send(StoryEvent.RESUME))
    }

    @Test fun `일반 사건은 대기열 최대 2개`() {
        send(StoryEvent.STOP)
        send(StoryEvent.TURN_LEFT)
        send(StoryEvent.RAIN_ON)
        send(StoryEvent.WARM)
        assertEquals(listOf("RAIN_ON 장면", "WARM 장면"), narrator.queued)
        assertEquals(0, speaker.speakWhileSpeaking)
    }

    @Test fun `WARM은 앞좌석도 받고, 앞좌석 직후의 뒷좌석은 우선해서 받는다`() {
        assertTrue(send(StoryEvent.WARM, rear = false))     // 앞좌석 허용 (시연용)
        clock.advance(2_000)
        assertTrue(send(StoryEvent.WARM, rear = true))      // 뒷좌석 우선: 8초 안이어도 받음
        clock.advance(2_000)
        assertFalse(send(StoryEvent.WARM, rear = false))    // 뒷좌석 직후의 앞좌석은 무시
        assertFalse(send(StoryEvent.WARM, rear = true))     // 뒷좌석끼리는 8초 중복 무시
    }

    @Test fun `시작 후 재생이 끝나고 25초 동안 사건이 없으면 IDLE 장면`() {
        send(StoryEvent.START)
        finishCurrent()
        clock.advance(24_999)
        assertEquals(1, speaker.spoken.size)
        clock.advance(1)
        assertEquals("IDLE 장면", speaker.spoken.last())
    }

    @Test fun `시작 전이나 멈춤 중에는 IDLE이 없다`() {
        send(StoryEvent.TURN_LEFT)
        finishCurrent()
        clock.advance(60_000)
        assertEquals(1, speaker.spoken.size)

        send(StoryEvent.START)
        send(StoryEvent.DOOR_OPEN)
        speaker.finished(); narrator.onStop()
        finishCurrent()
        clock.advance(60_000)
        assertEquals("DOOR_OPEN 장면", speaker.spoken.last())
    }

    @Test fun `이야기 잠시 멈추기를 누르면 지금 문장을 멈추고 START 전까지 사건을 무시한다`() {
        send(StoryEvent.START)
        send(StoryEvent.TURN_LEFT)
        director.stopStory()
        assertEquals(1, speaker.stopCount)
        assertTrue(narrator.queued.isEmpty())
        speaker.finished(); narrator.onStop()
        assertFalse(send(StoryEvent.STOP))
        assertFalse(send(StoryEvent.DOOR_OPEN))
        clock.advance(60_000)
        assertEquals(1, speaker.spoken.size)            // IDLE도 없음
        assertTrue(send(StoryEvent.START))
        assertTrue(send(StoryEvent.TURN_RIGHT))
    }
}
