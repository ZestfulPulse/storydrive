package com.zestfulpulse.storydrive.story

/**
 * 차량 신호 값의 변화를 사건 코드로 바꾼다 (작업지시서 4장). 순수 Kotlin, 단위 테스트 대상.
 *
 * 공통: 신호·영역마다 처음 들어온 값은 기준값으로만 저장하고 사건을 만들지 않는다.
 * (SDK가 구독 직후 현재 값을 보내기 때문에, 예를 들어 처음부터 풀려 있는 벨트로 멈추지 않게 한다.)
 *
 * 규칙
 * - STOP: 브레이크 뗌 → 밟음. 에뮬레이터는 기어 P에서 시험하므로 기어와 관계없이 만든다.
 * - GO: 브레이크 밟음 → 뗌, 단 기어가 D일 때만 (주차 중에 발을 떼는 것은 출발이 아님).
 * - TURN_LEFT / TURN_RIGHT: 방향지시등 OFF → LEFT/RIGHT.
 * - RAIN_ON / RAIN_OFF: 앞유리 와이퍼 OFF → ON / ON → OFF (뒷유리 와이퍼는 무시).
 * - WARM: 시트 열선 0 → 1 이상. 뒷좌석이면 rearSeat=true. 앞좌석도 시연용으로 인정한다.
 * - DOOR_OPEN: 승객 도어(ROW_*) 위치 0 → 1 이상. 후드·트렁크는 무시.
 * - BELT_WARN: 안전벨트 체결 → 풀림 (그 좌석이 한 번 체결된 뒤 풀린 경우만).
 * - RESUME: 열린 도어와 풀린 벨트가 모두 없어졌을 때 한 번.
 */
class EventDetector {
    private val last = mutableMapOf<String, Any?>()
    private val openDoors = mutableSetOf<String>()
    private val unbuckledSeats = mutableSetOf<String>()
    private var gear: String? = null

    /**
     * @param signal 신호 종류 이름 (VehicleSignalMonitor의 Signal.name과 같음)
     * @param area 영역 라벨 (예: "ROW_2_LEFT", "FRONT_WINDSHIELD"), 없으면 null
     * @param value 원시 값: 브레이크·벨트=Boolean, 기어·방향지시등·와이퍼=enum 이름, 열선·도어=Int
     */
    fun onSignal(signal: String, area: String?, value: Any?): List<DetectedEvent> {
        if (signal == GEAR) {
            gear = value as? String
            return emptyList()
        }
        val key = "$signal|${area.orEmpty()}"
        val isFirst = key !in last
        val previous = last[key]
        last[key] = value
        if (isFirst || previous == value) return emptyList()

        return when (signal) {
            BRAKE -> brake(previous as? Boolean, value as? Boolean)
            TURN_SIGNAL -> turn(previous as? String, value as? String)
            WIPER -> wiper(area, previous as? String, value as? String)
            SEAT_HEAT -> seatHeat(area, previous as? Int, value as? Int)
            DOOR -> door(area, previous as? Int, value as? Int)
            BELT_BUCKLED -> belt(area, previous as? Boolean, value as? Boolean)
            else -> emptyList()
        }
    }

    private fun brake(previous: Boolean?, now: Boolean?): List<DetectedEvent> = when {
        previous != true && now == true -> listOf(DetectedEvent(StoryEvent.STOP))
        previous == true && now == false && gear == GEAR_DRIVE -> listOf(DetectedEvent(StoryEvent.GO))
        else -> emptyList()
    }

    private fun turn(previous: String?, now: String?): List<DetectedEvent> {
        if (previous == now) return emptyList()
        return when (now) {
            "LEFT" -> listOf(DetectedEvent(StoryEvent.TURN_LEFT))
            "RIGHT" -> listOf(DetectedEvent(StoryEvent.TURN_RIGHT))
            else -> emptyList()
        }
    }

    private fun wiper(area: String?, previous: String?, now: String?): List<DetectedEvent> {
        if (area?.contains("FRONT") != true) return emptyList()
        return when {
            previous != "ON" && now == "ON" -> listOf(DetectedEvent(StoryEvent.RAIN_ON, area))
            previous == "ON" && now == "OFF" -> listOf(DetectedEvent(StoryEvent.RAIN_OFF, area))
            else -> emptyList()
        }
    }

    private fun seatHeat(area: String?, previous: Int?, now: Int?): List<DetectedEvent> {
        if ((previous ?: 0) <= 0 && (now ?: 0) > 0) {
            return listOf(DetectedEvent(StoryEvent.WARM, area, rearSeat = isRearSeat(area)))
        }
        return emptyList()
    }

    private fun door(area: String?, previous: Int?, now: Int?): List<DetectedEvent> {
        if (area == null || !area.startsWith("ROW_")) return emptyList()
        val wasOpen = (previous ?: 0) > 0
        val isOpen = (now ?: 0) > 0
        return when {
            !wasOpen && isOpen -> {
                openDoors += area
                listOf(DetectedEvent(StoryEvent.DOOR_OPEN, area))
            }
            wasOpen && !isOpen -> {
                openDoors -= area
                resumeIfClear(area)
            }
            else -> emptyList()
        }
    }

    private fun belt(area: String?, previous: Boolean?, now: Boolean?): List<DetectedEvent> {
        val seat = area ?: return emptyList()
        return when {
            previous == true && now == false -> {
                unbuckledSeats += seat
                listOf(DetectedEvent(StoryEvent.BELT_WARN, seat))
            }
            previous == false && now == true && seat in unbuckledSeats -> {
                unbuckledSeats -= seat
                resumeIfClear(seat)
            }
            else -> emptyList()
        }
    }

    private fun resumeIfClear(source: String): List<DetectedEvent> =
        if (openDoors.isEmpty() && unbuckledSeats.isEmpty()) listOf(DetectedEvent(StoryEvent.RESUME, source))
        else emptyList()

    companion object {
        const val BRAKE = "BRAKE"
        const val GEAR = "GEAR"
        const val TURN_SIGNAL = "TURN_SIGNAL"
        const val WIPER = "WIPER"
        const val SEAT_HEAT = "SEAT_HEAT"
        const val DOOR = "DOOR"
        const val BELT_BUCKLED = "BELT_BUCKLED"
        const val GEAR_DRIVE = "GEAR_DRIVE"

        /** 좌석 라벨이 뒷좌석(2열 이후)이면 true. */
        fun isRearSeat(area: String?): Boolean =
            area?.split("+")?.any { it.startsWith("ROW_2") || it.startsWith("ROW_3") } == true
    }
}
