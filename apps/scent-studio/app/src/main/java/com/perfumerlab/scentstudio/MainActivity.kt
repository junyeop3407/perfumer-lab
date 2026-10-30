package com.perfumerlab.scentstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 화면은 4단계에서 만든다 (docs/design.md 9·10절)
        }
    }
}
