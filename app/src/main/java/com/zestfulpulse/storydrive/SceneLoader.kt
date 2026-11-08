package com.zestfulpulse.storydrive

import android.content.Context
import android.util.Log
import com.zestfulpulse.storydrive.story.SceneBook
import com.zestfulpulse.storydrive.story.StoryEvent
import org.json.JSONObject

/** assets/scenes.json(작업지시서 5장)을 읽어 [SceneBook]을 만든다. 모르는 사건 이름은 건너뛴다. */
object SceneLoader {
    fun load(context: Context, fileName: String = "scenes.json"): SceneBook {
        val json = JSONObject(context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() })
        val scenesJson = json.getJSONObject("scenes")
        val scenes = mutableMapOf<StoryEvent, List<String>>()
        scenesJson.keys().forEach { key ->
            val event = StoryEvent.entries.firstOrNull { it.name == key }
            if (event == null) {
                Log.w(MainActivity.TAG, "scenes.json: 모르는 사건 $key 건너뜀")
                return@forEach
            }
            val array = scenesJson.getJSONArray(key)
            scenes[event] = List(array.length()) { array.getString(it) }
        }
        Log.i(MainActivity.TAG, "장면 불러옴: ${scenes.mapValues { it.value.size }}")
        return SceneBook(scenes)
    }
}
