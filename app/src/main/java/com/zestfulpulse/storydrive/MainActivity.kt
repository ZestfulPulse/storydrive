package com.zestfulpulse.storydrive

import android.os.Bundle
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val ttsStatus = mutableStateOf("TTS 준비 중…")
    private val support = mutableStateMapOf<Signal, Boolean>()
    private val signalLog = mutableStateListOf<String>()

    private lateinit var speaker: KoreanSpeaker
    private lateinit var monitor: VehicleSignalMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity created")
        speaker = KoreanSpeaker(this) { message -> runOnUiThread { ttsStatus.value = message } }
        monitor = VehicleSignalMonitor(this, object : VehicleSignalMonitor.Listener {
            override fun onSupport(signal: Signal, supported: Boolean, detail: String) =
                runOnUiThread { support[signal] = supported }

            override fun onSignal(signal: Signal, area: String?, value: String) = runOnUiThread {
                val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
                signalLog.add(0, "$time  ${signal.label}${area?.let { " ($it)" } ?: ""}: $value")
                if (signalLog.size > MAX_LOG) signalLog.removeAt(signalLog.lastIndex)
            }
        })
        monitor.start()

        setContent {
            Row(
                modifier = Modifier.fillMaxSize().background(SKY).padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "스토리드라이브", fontSize = 40.sp, color = INK)
                    Button(onClick = { speaker.speak(SAMPLE_SENTENCE) }) {
                        Text(text = "한국어 문장 읽기", fontSize = 24.sp)
                    }
                    Text(text = ttsStatus.value, fontSize = 18.sp, color = INK)
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = "차량 신호", fontSize = 26.sp, color = INK)
                    Signal.entries.forEach { signal ->
                        val state = when (support[signal]) {
                            true -> "구독 중"
                            false -> "이 차량에서는 사용 안 함"
                            null -> "확인 중…"
                        }
                        Text(text = "${signal.label}: $state", fontSize = 18.sp, color = INK)
                    }
                    Text(text = "최근 신호", fontSize = 22.sp, color = INK, modifier = Modifier.padding(top = 12.dp))
                    signalLog.forEach { Text(text = it, fontSize = 16.sp, color = INK) }
                }
            }
        }
    }

    override fun onDestroy() {
        monitor.stop()
        speaker.shutdown()
        super.onDestroy()
    }

    companion object {
        const val TAG = "StoryDrive"
        const val SAMPLE_SENTENCE = "안녕, 나는 스토리드라이브야. 오늘은 어떤 이야기를 들려줄까?"
        private const val MAX_LOG = 12
        private val SKY = Color(0xFFCFE4F2)
        private val INK = Color(0xFF233050)
    }
}
