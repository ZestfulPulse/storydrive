package com.zestfulpulse.storydrive

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val status = mutableStateOf("TTS 준비 중…")
    private lateinit var speaker: KoreanSpeaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity created")
        speaker = KoreanSpeaker(this) { message -> runOnUiThread { status.value = message } }

        setContent {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFFCFE4F2)).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "스토리드라이브", fontSize = 40.sp, color = Color(0xFF233050))
                Button(onClick = { speaker.speak(SAMPLE_SENTENCE) }) {
                    Text(text = "한국어 문장 읽기", fontSize = 24.sp)
                }
                Text(text = status.value, fontSize = 18.sp, color = Color(0xFF233050))
            }
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    companion object {
        const val TAG = "StoryDrive"
        const val SAMPLE_SENTENCE = "안녕, 나는 스토리드라이브야. 오늘은 어떤 이야기를 들려줄까?"
    }
}
