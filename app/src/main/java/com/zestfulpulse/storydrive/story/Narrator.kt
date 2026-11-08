package com.zestfulpulse.storydrive.story

/** 음성 엔진 추상화 (실제 구현: KoreanSpeaker / Pleos TTS ON_DEVICE). */
interface Speaker {
    /** 재생을 요청한다. 준비가 안 됐으면 false. */
    fun speak(text: String): Boolean
    fun stop()
}

/** 지연 실행 추상화 (실제 구현: Android Handler, 테스트: FakeScheduler). */
interface Scheduler {
    fun schedule(delayMs: Long, action: () -> Unit): Cancellable
}

fun interface Cancellable {
    fun cancel()
}

/**
 * 문장 재생 대기열 (작업지시서 M3 음성 재생 규칙). 모든 호출은 한 스레드(메인)에서 한다.
 *
 * - 재생 중에는 speak()를 부르지 않는다. 다음 문장은 대기열(최대 [maxQueue]개, 넘치면 오래된 것부터 버림)에 둔다.
 * - onDone 또는 onError를 받으면 다음 문장을 재생한다.
 * - 예상 시간 타이머: 글자 수 × 0.25초 × 1.5 + 2초 안에 끝 신호가 없으면 stop() 후 다음으로 넘어간다.
 * - 발화마다 번호를 붙이고, onDone·onStop·onError·타이머 중 처음 온 하나만 종료로 처리한다.
 *   Pleos TTS 콜백에는 발화 번호가 없으므로, onDone·onStop은 그 발화의 onStart를 받은 뒤에만 인정한다.
 *   (앞 발화의 늦은 콜백이 새 발화를 끝내지 않게 한다. onError는 항상 인정한다.)
 * - [interrupt]: 대기열을 비우고 지금 문장을 stop()한 뒤, onStop(또는 [stopTimeoutMs] 대기)을 받고 나서 새 문장을 재생한다.
 *   (stop()이 부르는 onStop이 새 문장의 종료로 잘못 처리되지 않게 한다.)
 */
class Narrator(
    private val speaker: Speaker,
    private val scheduler: Scheduler,
    private val maxQueue: Int = 2,
    private val stopTimeoutMs: Long = 1_500,
    private val log: (String) -> Unit = {},
) {
    private sealed interface State {
        data object Idle : State
        data class Speaking(val id: Int, val text: String, val timer: Cancellable, var started: Boolean = false) : State
        data class Stopping(val timer: Cancellable) : State
    }

    private var state: State = State.Idle
    private val queue = ArrayDeque<String>()
    private var nextId = 0

    /** 대기열이 비고 재생도 끝났을 때 불린다 (IDLE 타이머용). */
    var onIdle: () -> Unit = {}

    val isIdle get() = state == State.Idle && queue.isEmpty()
    val queued: List<String> get() = queue.toList()
    val currentText: String? get() = (state as? State.Speaking)?.text

    /** 일반 문장: 재생 중이면 대기열에 넣는다. */
    fun enqueue(text: String) {
        if (state == State.Idle) {
            play(text)
            return
        }
        queue.addLast(text)
        while (queue.size > maxQueue) {
            val dropped = queue.removeFirst()
            log("대기열 초과, 버림: $dropped")
        }
    }

    /** 안전 문장 등: 지금 문장과 대기열을 버리고 이 문장을 바로 재생한다. */
    fun interrupt(text: String) {
        queue.clear()
        queue.addLast(text)
        when (val s = state) {
            is State.Speaking -> beginStop(s, reason = "끊기")
            is State.Stopping -> Unit // onStop을 기다리는 중. 끝나면 대기열의 새 문장이 재생된다.
            State.Idle -> playNext()
        }
    }

    /** 대기열을 비우고 지금 문장도 멈춘다. */
    fun clear() {
        queue.clear()
        (state as? State.Speaking)?.let { beginStop(it, reason = "멈춤") }
    }

    // --- Speaker 이벤트 (Pleos TTS 콜백에는 발화 번호가 없으므로 "지금 발화"에 대한 것으로 본다) ---

    fun onStart() {
        val s = state as? State.Speaking ?: return log("무시: 재생 중이 아닐 때 onStart")
        s.started = true
    }

    fun onDone() = finishIfStarted("onDone")
    fun onError() = finishCurrent("onError")

    fun onStop() {
        when (val s = state) {
            is State.Stopping -> {
                s.timer.cancel()
                state = State.Idle
                playNext()
            }
            is State.Speaking -> finishIfStarted("onStop")
            State.Idle -> log("무시: 재생 중이 아닐 때 onStop")
        }
    }

    private fun finishIfStarted(reason: String) {
        val s = state as? State.Speaking
        if (s != null && !s.started) {
            log("무시: #${s.id} onStart 전에 온 $reason (앞 발화의 늦은 콜백)")
            return
        }
        finishCurrent(reason)
    }

    // --- 내부 ---

    private fun play(text: String) {
        val id = ++nextId
        val timer = scheduler.schedule(expectedDurationMs(text)) { onTimeout(id) }
        state = State.Speaking(id, text, timer)
        log("재생 #$id: $text")
        if (!speaker.speak(text)) {
            log("speak 실패 #$id")
            finishCurrent("speak 실패")
        }
    }

    private fun finishCurrent(reason: String) {
        val s = state as? State.Speaking
        if (s == null) {
            log("무시: 이미 끝난 발화의 $reason")
            return
        }
        s.timer.cancel()
        log("종료 #${s.id} ($reason)")
        state = State.Idle
        playNext()
    }

    private fun onTimeout(id: Int) {
        val s = state as? State.Speaking ?: return
        if (s.id != id) return
        beginStop(s, reason = "예상 시간 초과")
    }

    private fun beginStop(s: State.Speaking, reason: String) {
        s.timer.cancel()
        log("종료 #${s.id} ($reason) → stop()")
        val waitTimer = scheduler.schedule(stopTimeoutMs) { onStopTimeout() }
        state = State.Stopping(waitTimer)
        speaker.stop()
    }

    private fun onStopTimeout() {
        if (state !is State.Stopping) return
        log("onStop 대기 시간 초과, 다음으로")
        state = State.Idle
        playNext()
    }

    private fun playNext() {
        val next = queue.removeFirstOrNull()
        if (next != null) play(next) else onIdle()
    }

    companion object {
        /** 예상 재생 시간: 글자 수 × 0.25초 × 1.5 + 2초 */
        fun expectedDurationMs(text: String): Long = (text.length * 250L * 3 / 2) + 2_000L
    }
}
