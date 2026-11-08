package com.zestfulpulse.storydrive.story

/** 작업지시서 4장의 사건 코드. RAIN_HARD는 와이퍼 속도를 알 수 없어 쓰지 않는다. */
enum class StoryEvent(val label: String, val isSafety: Boolean = false) {
    START("이야기 시작"),
    STOP("정차"),
    GO("출발"),
    TURN_LEFT("왼쪽 깜빡이"),
    TURN_RIGHT("오른쪽 깜빡이"),
    RAIN_ON("비 시작"),
    RAIN_OFF("비 그침"),
    WARM("열선 켜짐"),
    DOOR_OPEN("문 열림", isSafety = true),
    BELT_WARN("안전벨트 풀림", isSafety = true),
    RESUME("이어가기"),
    IDLE("조용한 시간"),
}

/** 감지된 사건. WARM이면 [rearSeat]로 뒷좌석에서 왔는지 구분한다. [source]는 로그용 신호 위치. */
data class DetectedEvent(
    val event: StoryEvent,
    val source: String? = null,
    val rearSeat: Boolean = false,
)
