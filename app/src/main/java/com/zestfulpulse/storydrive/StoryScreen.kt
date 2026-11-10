package com.zestfulpulse.storydrive

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 화면 색 (작업지시서 6장) */
object StoryColors {
    val Sky = Color(0xFFCFE4F2)
    val Paper = Color(0xFFFFF8EC)
    val Ink = Color(0xFF233050)
    val Orange = Color(0xFFF27A2E)
}

/** "차가 들려준 신호" 목록 한 줄 */
data class SignalEntry(val time: String, val text: String)

/** 개발용 표시 (디버그 표시를 켰을 때만 보임) */
data class DebugInfo(val support: List<String>, val rawSignals: List<String>, val status: String)

/**
 * 이야기 재생 화면 (작업지시서 6장, 시안 1번 단순화). 가로형, 큰 글씨.
 * - 상단: 앱 이름. **앱 이름을 길게 누르면 디버그 표시를 켜고 끈다** (녹화 때는 꺼 둔다).
 * - 왼쪽: 작은 제목 + 지금 읽는 문장(큰 글씨)
 * - 오른쪽: "차가 들려준 신호" 목록, 방금 반영된 항목은 주황색
 * - 아래: "이야기 시작", "이야기 잠시 멈추기"
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryScreen(
    currentLine: String,
    entries: List<SignalEntry>,
    canStart: Boolean,
    showDebug: Boolean,
    debug: DebugInfo,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onToggleDebug: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(StoryColors.Sky).padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "스토리드라이브",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = StoryColors.Ink,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onToggleDebug),
        )

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // 왼쪽: 지금 읽는 문장
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .background(StoryColors.Paper, RoundedCornerShape(28.dp))
                    .padding(40.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "불꼬와 구름 섬 가는 길", fontSize = 24.sp, color = StoryColors.Ink.copy(alpha = 0.7f))
                Spacer(Modifier.height(24.dp))
                Text(
                    text = currentLine.ifEmpty { "\"이야기 시작\"을 누르면 불꼬가 이야기를 들려줘요." },
                    fontSize = 46.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Medium,
                    color = StoryColors.Ink,
                )
            }

            // 오른쪽: 차가 들려준 신호
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(text = "차가 들려준 신호", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = StoryColors.Ink)
                if (entries.isEmpty()) {
                    Text(text = "아직 신호가 없어요", fontSize = 24.sp, color = StoryColors.Ink.copy(alpha = 0.6f))
                }
                entries.forEachIndexed { index, entry ->
                    val highlight = index == 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.text,
                            fontSize = if (highlight) 30.sp else 26.sp,
                            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                            color = if (highlight) StoryColors.Orange else StoryColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(text = entry.time, fontSize = 18.sp, color = StoryColors.Ink.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // 아래: 버튼
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onStart,
                enabled = canStart,
                colors = ButtonDefaults.buttonColors(containerColor = StoryColors.Orange),
                modifier = Modifier.height(80.dp).width(320.dp),
            ) { Text(text = "이야기 시작", fontSize = 30.sp) }
            OutlinedButton(
                onClick = onPause,
                modifier = Modifier.height(80.dp).width(360.dp),
            ) { Text(text = "이야기 잠시 멈추기", fontSize = 28.sp, color = StoryColors.Ink) }
            if (showDebug) {
                Text(text = debug.status, fontSize = 16.sp, color = StoryColors.Ink)
            }
        }

        if (showDebug) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column {
                        Text(text = "[디버그] 신호 지원 상태", fontSize = 16.sp, color = StoryColors.Ink)
                        debug.support.forEach { Text(text = it, fontSize = 14.sp, color = StoryColors.Ink) }
                    }
                    Column {
                        Text(text = "[디버그] 최근 원시 신호", fontSize = 16.sp, color = StoryColors.Ink)
                        debug.rawSignals.take(8).forEach { Text(text = it, fontSize = 14.sp, color = StoryColors.Ink) }
                    }
                }
            }
        }
    }
}
