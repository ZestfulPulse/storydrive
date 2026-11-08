package com.zestfulpulse.storydrive

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    private val acceptedEvents = mutableStateListOf<String>()
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
        monitor.start()

        setContent {
            Row(
                modifier = Modifier.fillMaxSize().background(SKY).padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "스토리드라이브", fontSize = 36.sp, color = INK)
                    Text(text = "불꼬와 구름 섬 가는 길", fontSize = 18.sp, color = INK)
                    Text(text = currentLine.value.ifEmpty { "…" }, fontSize = 30.sp, color = INK)
                    Button(onClick = { director.handle(DetectedEvent(StoryEvent.START)); refreshLine() }) {
                        Text(text = "이야기 시작", fontSize = 24.sp)
                    }
                    Text(text = status.value, fontSize = 16.sp, color = INK)
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = "차가 들려준 신호", fontSize = 24.sp, color = INK)
                    acceptedEvents.forEachIndexed { i, line ->
                        Text(text = line, fontSize = 18.sp, color = if (i == 0) ORANGE else INK)
                    }
                    Text(text = "신호 지원 상태", fontSize = 18.sp, color = INK, modifier = Modifier.padding(top = 12.dp))
                    Signal.entries.forEach { signal ->
                        val state = when (support[signal]) {
                            true -> "구독 중"
                            false -> "이 차량에서는 사용 안 함"
                            null -> "확인 중…"
                        }
                        Text(text = "${signal.label}: $state", fontSize = 14.sp, color = INK)
                    }
                    Text(text = "최근 원시 신호", fontSize = 18.sp, color = INK, modifier = Modifier.padding(top = 12.dp))
                    signalLog.take(6).forEach { Text(text = it, fontSize = 14.sp, color = INK) }
                }
            }
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        monitor.stop()
        speaker.shutdown()
        super.onDestroy()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun refreshLine() {
        narrator.currentText?.let { currentLine.value = it }
    }

    private fun addAccepted(d: DetectedEvent) {
        val where = d.source?.let { " ($it)" } ?: ""
        addLog(acceptedEvents, "${d.event.label}$where")
    }

    private fun addLog(list: MutableList<String>, text: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        list.add(0, "$time  $text")
        if (list.size > MAX_LOG) list.removeAt(list.lastIndex)
    }

    companion object {
        const val TAG = "StoryDrive"
        private const val MAX_LOG = 8
        private val SKY = Color(0xFFCFE4F2)
        private val INK = Color(0xFF233050)
        private val ORANGE = Color(0xFFF27A2E)
    }
}
