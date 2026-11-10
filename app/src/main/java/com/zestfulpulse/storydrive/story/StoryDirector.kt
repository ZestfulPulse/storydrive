package com.zestfulpulse.storydrive.story

/**
 * 사건을 받아 장면을 고르고 [Narrator]에 넘긴다 (작업지시서 4장 처리 규칙).
 *
 * - 우선순위: DOOR_OPEN·BELT_WARN은 지금 문장을 즉시 끊고 대기열을 비운 뒤 안내 문장을 재생한다. 이후 RESUME 전까지 멈춤 상태.
 * - 멈춤 상태에서는 일반 사건을 재생하지 않는다 (대기열에도 넣지 않음).
 * - 같은 사건이 [dedupeMs](8초) 안에 다시 오면 무시한다. 안전 사건과 RESUME은 안전을 위해 중복 무시를 적용하지 않는다.
 * - WARM: 뒷좌석 우선, 앞좌석 허용. 앞좌석 WARM 뒤 8초 안에 뒷좌석 WARM이 오면 무시하지 않고 받아들인다.
 *   (그 반대, 즉 뒷좌석 WARM 뒤의 앞좌석 WARM은 무시한다.)
 * - IDLE: START 이후, 멈춤이 아니고 재생이 끝난 뒤 [idleMs](25초) 동안 사건이 없으면 IDLE 장면.
 * - 사용자가 "이야기 잠시 멈추기"를 누르면([stopStory]) 다시 START 할 때까지 모든 사건을 무시한다.
 *   (앱을 켠 뒤 처음 START 전에는 사건에 반응한다.)
 */
class StoryDirector(
    private val scenes: SceneBook,
    private val narrator: Narrator,
    private val scheduler: Scheduler,
    private val now: () -> Long,
    private val dedupeMs: Long = 8_000,
    private val idleMs: Long = 25_000,
    private val log: (String) -> Unit = {},
) {
    private val lastAccepted = mutableMapOf<StoryEvent, Long>()
    private var lastWarmWasRear = false
    private var idleTimer: Cancellable? = null

    var started = false
        private set
    var paused = false
        private set
    var stoppedByUser = false
        private set

    /** 반영된 사건 알림 (화면 목록용) */
    var onAccepted: (DetectedEvent) -> Unit = {}

    init {
        narrator.onIdle = { scheduleIdle() }
    }

    fun handle(detected: DetectedEvent): Boolean {
        val event = detected.event
        if (stoppedByUser && event != StoryEvent.START) {
            log("이야기 멈춤(사용자) 중 무시: ${event.name}")
            return false
        }
        if (isDuplicate(detected)) {
            log("중복 무시: ${event.name}")
            return false
        }
        if (paused && !event.isSafety && event != StoryEvent.RESUME) {
            log("멈춤 중 무시: ${event.name}")
            return false
        }
        if (event == StoryEvent.RESUME && !paused) {
            log("멈춤이 아니어서 RESUME 무시")
            return false
        }
        val line = scenes.pick(event)
        if (line == null) {
            log("장면 없음: ${event.name}")
            return false
        }

        lastAccepted[event] = now()
        if (event == StoryEvent.WARM) lastWarmWasRear = detected.rearSeat
        cancelIdle()

        when {
            event.isSafety -> {
                paused = true
                narrator.interrupt(line)
            }
            event == StoryEvent.RESUME -> {
                paused = false
                narrator.interrupt(line)
            }
            else -> {
                if (event == StoryEvent.START) {
                    started = true
                    stoppedByUser = false
                }
                narrator.enqueue(line)
            }
        }
        log("반영: ${event.name}${detected.source?.let { " ($it)" } ?: ""} → $line")
        onAccepted(detected)
        return true
    }

    /** 사용자가 "이야기 잠시 멈추기"를 눌렀을 때 (M4에서 사용) */
    fun stopStory() {
        started = false
        stoppedByUser = true
        cancelIdle()
        narrator.clear()
    }

    private fun isDuplicate(d: DetectedEvent): Boolean {
        if (d.event.isSafety || d.event == StoryEvent.RESUME) return false
        val last = lastAccepted[d.event] ?: return false
        if (now() - last >= dedupeMs) return false
        // 뒷좌석 우선: 직전 WARM이 앞좌석이었으면 뒷좌석 WARM은 받아들인다.
        if (d.event == StoryEvent.WARM && d.rearSeat && !lastWarmWasRear) return false
        return true
    }

    private fun scheduleIdle() {
        cancelIdle()
        if (!started || paused) return
        idleTimer = scheduler.schedule(idleMs) {
            idleTimer = null
            if (started && !paused && narrator.isIdle) handle(DetectedEvent(StoryEvent.IDLE))
        }
    }

    private fun cancelIdle() {
        idleTimer?.cancel()
        idleTimer = null
    }
}
