package com.zestfulpulse.storydrive.story

import com.zestfulpulse.storydrive.story.EventDetector.Companion.BELT_BUCKLED
import com.zestfulpulse.storydrive.story.EventDetector.Companion.BRAKE
import com.zestfulpulse.storydrive.story.EventDetector.Companion.DOOR
import com.zestfulpulse.storydrive.story.EventDetector.Companion.GEAR
import com.zestfulpulse.storydrive.story.EventDetector.Companion.SEAT_HEAT
import com.zestfulpulse.storydrive.story.EventDetector.Companion.TURN_SIGNAL
import com.zestfulpulse.storydrive.story.EventDetector.Companion.WIPER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetectorTest {
    private val d = EventDetector()
    private fun events(signal: String, area: String?, value: Any?) = d.onSignal(signal, area, value).map { it.event }

    @Test fun `처음 들어온 값은 기준값이라 사건이 없다`() {
        assertEquals(emptyList<StoryEvent>(), events(BRAKE, null, true))
        assertEquals(emptyList<StoryEvent>(), events(BELT_BUCKLED, "ROW_2_LEFT", false))
        assertEquals(emptyList<StoryEvent>(), events(DOOR, "ROW_1_LEFT", 1))
    }

    @Test fun `브레이크 밟으면 STOP, 기어 D에서 떼면 GO`() {
        events(BRAKE, null, false)
        assertEquals(listOf(StoryEvent.STOP), events(BRAKE, null, true))
        assertEquals(emptyList<StoryEvent>(), events(BRAKE, null, false)) // 기어 P(모름)에서는 GO 없음
        d.onSignal(GEAR, null, "GEAR_DRIVE")
        events(BRAKE, null, true)
        assertEquals(listOf(StoryEvent.GO), events(BRAKE, null, false))
    }

    @Test fun `방향지시등 OFF에서 LEFT, RIGHT로 바뀌면 회전 사건`() {
        events(TURN_SIGNAL, null, "OFF")
        assertEquals(listOf(StoryEvent.TURN_LEFT), events(TURN_SIGNAL, null, "LEFT"))
        assertEquals(emptyList<StoryEvent>(), events(TURN_SIGNAL, null, "OFF"))
        assertEquals(listOf(StoryEvent.TURN_RIGHT), events(TURN_SIGNAL, null, "RIGHT"))
    }

    @Test fun `앞유리 와이퍼 켜짐은 RAIN_ON, 꺼짐은 RAIN_OFF, 뒷유리는 무시`() {
        events(WIPER, "FRONT_WINDSHIELD", "OFF")
        events(WIPER, "REAR_WINDSHIELD", "OFF")
        assertEquals(listOf(StoryEvent.RAIN_ON), events(WIPER, "FRONT_WINDSHIELD", "ON"))
        assertEquals(listOf(StoryEvent.RAIN_OFF), events(WIPER, "FRONT_WINDSHIELD", "OFF"))
        assertEquals(emptyList<StoryEvent>(), events(WIPER, "REAR_WINDSHIELD", "ON"))
    }

    @Test fun `열선 0에서 1 이상이면 WARM, 뒷좌석 여부를 함께 준다`() {
        d.onSignal(SEAT_HEAT, "ROW_2_LEFT", 0)
        d.onSignal(SEAT_HEAT, "ROW_1_LEFT", 0)
        val rear = d.onSignal(SEAT_HEAT, "ROW_2_LEFT", 1).single()
        assertEquals(StoryEvent.WARM, rear.event)
        assertTrue(rear.rearSeat)
        val front = d.onSignal(SEAT_HEAT, "ROW_1_LEFT", 2).single()
        assertEquals(StoryEvent.WARM, front.event)
        assertFalse(front.rearSeat)
        assertEquals(emptyList<StoryEvent>(), events(SEAT_HEAT, "ROW_1_LEFT", 3)) // 이미 켜진 상태에서 단계만 바뀜
    }

    @Test fun `승객 도어가 열리면 DOOR_OPEN, 모두 닫히면 RESUME, 트렁크는 무시`() {
        listOf("ROW_1_LEFT", "ROW_2_LEFT", "REAR").forEach { d.onSignal(DOOR, it, 0) }
        assertEquals(listOf(StoryEvent.DOOR_OPEN), events(DOOR, "ROW_2_LEFT", 1))
        assertEquals(listOf(StoryEvent.DOOR_OPEN), events(DOOR, "ROW_1_LEFT", 1))
        assertEquals(emptyList<StoryEvent>(), events(DOOR, "ROW_2_LEFT", 0)) // 아직 하나 열림
        assertEquals(listOf(StoryEvent.RESUME), events(DOOR, "ROW_1_LEFT", 0))
        assertEquals(emptyList<StoryEvent>(), events(DOOR, "REAR", 1))
    }

    @Test fun `벨트는 채움에서 풂으로 바뀔 때만 BELT_WARN, 다시 채우면 RESUME`() {
        d.onSignal(BELT_BUCKLED, "ROW_2_LEFT", false)
        assertEquals(emptyList<StoryEvent>(), events(BELT_BUCKLED, "ROW_2_LEFT", true)) // 처음 채움은 사건 아님
        assertEquals(listOf(StoryEvent.BELT_WARN), events(BELT_BUCKLED, "ROW_2_LEFT", false))
        assertEquals(listOf(StoryEvent.RESUME), events(BELT_BUCKLED, "ROW_2_LEFT", true))
    }

    @Test fun `문과 벨트가 모두 정상이 되어야 RESUME`() {
        d.onSignal(DOOR, "ROW_2_LEFT", 0)
        d.onSignal(BELT_BUCKLED, "ROW_2_RIGHT", true)
        events(DOOR, "ROW_2_LEFT", 1)
        events(BELT_BUCKLED, "ROW_2_RIGHT", false)
        assertEquals(emptyList<StoryEvent>(), events(DOOR, "ROW_2_LEFT", 0))
        assertEquals(listOf(StoryEvent.RESUME), events(BELT_BUCKLED, "ROW_2_RIGHT", true))
    }

    @Test fun `뒷좌석 판별`() {
        assertTrue(EventDetector.isRearSeat("ROW_2_RIGHT"))
        assertTrue(EventDetector.isRearSeat("ROW_3_LEFT"))
        assertFalse(EventDetector.isRearSeat("ROW_1_LEFT"))
        assertFalse(EventDetector.isRearSeat(null))
    }
}
