package com.zestfulpulse.storydrive

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity created")
        setContent {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFCFE4F2)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "스토리드라이브", fontSize = 40.sp, color = Color(0xFF233050))
            }
        }
    }

    companion object {
        const val TAG = "StoryDrive"
    }
}
