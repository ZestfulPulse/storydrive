package com.zestfulpulse.storydrive.story

/** 시간을 직접 움직이는 테스트용 스케줄러 */
class FakeScheduler {
    var now = 0L
        private set

    private data class Task(val at: Long, val seq: Int, val action: () -> Unit, var cancelled: Boolean = false)

    private val tasks = mutableListOf<Task>()
    private var seq = 0

    val scheduler = object : Scheduler {
        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
            val task = Task(now + delayMs, seq++, action)
            tasks += task
            return Cancellable { task.cancelled = true }
        }
    }

    fun advance(ms: Long) {
        val target = now + ms
        while (true) {
            val next = tasks.filter { !it.cancelled && it.at <= target }.minWithOrNull(compareBy({ it.at }, { it.seq })) ?: break
            tasks.remove(next)
            now = next.at
            next.action()
        }
        now = target
    }
}

/** speak/stop 호출을 기록하는 테스트용 음성 엔진 */
class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    var stopCount = 0
    var speaking = false
        private set
    /** 재생 중에 speak가 불린 횟수 (0이어야 함) */
    var speakWhileSpeaking = 0
    var ready = true

    override fun speak(text: String): Boolean {
        if (!ready) return false
        if (speaking) speakWhileSpeaking++
        speaking = true
        spoken += text
        return true
    }

    override fun stop() {
        stopCount++
    }

    /** 엔진이 재생을 마쳤다고 가정 (Narrator에 콜백을 넘기기 전에 호출) */
    fun finished() {
        speaking = false
    }
}
