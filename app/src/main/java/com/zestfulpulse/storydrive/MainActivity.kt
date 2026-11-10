package com.zestfulpulse.storydrive

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.zestfulpulse.storydrive.story.Cancellable
import com.zestfulpulse.storydrive.story.DetectedEvent
import com.zestfulpulse.storydrive.story.EventDetector
import com.zestfulpulse.storydrive.story.Narrator
import com.zestfulpulse.storydrive.story.Scheduler
import com.zestfulpulse.storydrive.story.StoryDirector
import com.zestfulpulse.storydrive.story.StoryEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val main = Handler(Looper.getMainLooper())

    private val status = mutableStateOf("음성 준비 중…")
    private val currentLine = mutableStateOf("")
    private val support = mutableStateMapOf<Signal, Boolean>()
    private val acceptedEvents = mutableStateListOf<SignalEntry>()
    private val showDebug = mutableStateOf(false)
    private val signalLog = mutableStateListOf<String>()

    private lateinit var speaker: KoreanSpeaker
    private lateinit var narrator: Narrator
    private lateinit var director: StoryDirector
    private lateinit var monitor: VehicleSignalMonitor
    private val detector = EventDetector()

    /** 메인 스레드 Handler 기반 스케줄러. Narrator·StoryDirector는 모두 메인 스레드에서만 다룬다. */
    private val scheduler = object : Scheduler {
        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
            val runnable = Runnable(action)
            main.postDelayed(runnable, delayMs)
            return Cancellable { main.removeCallbacks(runnable) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity created")

        speaker = KoreanSpeaker(this, object : KoreanSpeaker.Callbacks {
            override fun onStatus(message: String) = onMain { status.value = message }
            override fun onStart() = onMain { narrator.onStart() }
            override fun onDone() = onMain { narrator.onDone(); refreshLine() }
            override fun onStop() = onMain { narrator.onStop(); refreshLine() }
            override fun onError() = onMain { narrator.onError(); refreshLine() }
        })
        narrator = Narrator(speaker, scheduler, log = { Log.i(TAG, "[재생] $it") })
        director = StoryDirector(
            scenes = SceneLoader.load(this),
            narrator = narrator,
            scheduler = scheduler,
            now = SystemClock::elapsedRealtime,
            log = { Log.i(TAG, "[이야기] $it") },
        )
        director.onAccepted = { d -> addAccepted(d); refreshLine() }

        monitor = VehicleSignalMonitor(this, object : VehicleSignalMonitor.Listener {
            override fun onSupport(signal: Signal, supported: Boolean, detail: String) =
                onMain { support[signal] = supported }

            override fun onSignal(signal: Signal, area: String?, place: String?, value: String, raw: Any?) = onMain {
                addLog(signalLog, "${signal.label}${place?.let { " ($it)" } ?: ""}: $value")
                detector.onSignal(signal.name, area, raw).forEach { director.handle(it) }
                refreshLine()
            }
        })

        setContent {
            StoryScreen(
                currentLine = currentLine.value,
                entries = acceptedEvents.toList(),
                // 차량 신호 확인은 백그라운드에서 신호별로 끝난다. 모두 끝날 때까지 신호 목록 자리에 진행 상황을 보인다.
                vehicleStatus = if (support.size < Signal.entries.size) {
                    "차량 신호 연결 중 (${support.size}/${Signal.entries.size})"
                } else {
                    null
                },
                canStart = true, // 주행 중 비활성은 M5에서
                showDebug = showDebug.value,
                debug = DebugInfo(
                    support = Signal.entries.map { signal ->
                        val state = when (support[signal]) {
                            true -> "구독 중"
                            false -> "이 차량에서는 사용 안 함"
                            null -> "확인 중…"
                        }
                        "${signal.label}: $state"
                    },
                    rawSignals = signalLog.toList(),
                    status = status.value,
                ),
                onStart = {
                    Log.i(TAG, "[버튼] 이야기 시작")
                    director.handle(DetectedEvent(StoryEvent.START))
                    refreshLine()
                },
                onPause = {
                    Log.i(TAG, "[버튼] 이야기 잠시 멈추기")
                    director.stopStory()
                    currentLine.value = "이야기를 잠시 멈췄어요. \"이야기 시작\"을 누르면 다시 들려줄게요."
                },
                onToggleDebug = { showDebug.value = !showDebug.value },
            )
        }

        // 앱 실행 시간(심사 기준 10초) 단축: 첫 화면을 먼저 그린 뒤 음성·차량 SDK를 시작한다.
        // 차량 SDK는 monitor 안의 전용 스레드에서 돌고, 여기서는 작업을 넘기기만 한다.
        window.decorView.post {
            if (isDestroyed) return@post
            Log.i(TAG, "첫 화면 이후 SDK 시작")
            speaker.start()
            monitor.start()
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        monitor.stop()
        speaker.shutdown()
        super.onDestroy()
    }

    /** SDK 스레드에서 온 콜백을 메인 스레드로 넘긴다. 화면이 이미 닫혔으면 버린다. */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!isDestroyed) block()
        } else {
            main.post { if (!isDestroyed) block() }
        }
    }

    private fun refreshLine() {
        narrator.currentText?.let { currentLine.value = it }
    }

    /** "차가 들려준 신호" 목록에 추가. 차 신호가 아닌 START·IDLE은 넣지 않는다. */
    private fun addAccepted(d: DetectedEvent) {
        val text = when (d.event) {
            StoryEvent.STOP -> "브레이크를 밟았어요"
            StoryEvent.GO -> "다시 출발했어요"
            StoryEvent.TURN_LEFT -> "왼쪽 깜빡이를 켰어요"
            StoryEvent.TURN_RIGHT -> "오른쪽 깜빡이를 켰어요"
            StoryEvent.RAIN_ON -> "와이퍼가 움직여요"
            StoryEvent.RAIN_OFF -> "와이퍼가 멈췄어요"
            StoryEvent.WARM -> if (d.rearSeat) "뒷좌석 열선이 켜졌어요" else "열선이 켜졌어요"
            StoryEvent.DOOR_OPEN -> "문이 열렸어요"
            StoryEvent.BELT_WARN -> "안전벨트가 풀렸어요"
            StoryEvent.RESUME -> "다시 준비됐어요"
            StoryEvent.START, StoryEvent.IDLE -> return
        }
        val time = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
        acceptedEvents.add(0, SignalEntry(time, text))
        if (acceptedEvents.size > MAX_ENTRIES) acceptedEvents.removeAt(acceptedEvents.lastIndex)
    }

    private fun addLog(list: MutableList<String>, text: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        list.add(0, "$time  $text")
        if (list.size > MAX_LOG) list.removeAt(list.lastIndex)
    }

    companion object {
        const val TAG = "StoryDrive"
        private const val MAX_LOG = 8
        private const val MAX_ENTRIES = 7
    }
}
