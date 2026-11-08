package com.zestfulpulse.storydrive.story

import kotlin.random.Random

/**
 * 사건별 장면 문장 모음 (assets/scenes.json). JSON 파싱은 Android 쪽에서 하고, 여기서는 고르기와 채우기만 한다.
 * - 같은 사건의 문장이 여러 개면 직전에 고른 것과 다른 것을 고른다 (4장 규칙 4).
 * - {아이1}, {아이2}는 앱 안에서만 채운다. 시제품 기본값은 "대장", "부대장".
 */
class SceneBook(
    private val scenes: Map<StoryEvent, List<String>>,
    private val child1: String = DEFAULT_CHILD_1,
    private val child2: String = DEFAULT_CHILD_2,
    private val random: Random = Random.Default,
) {
    private val lastIndex = mutableMapOf<StoryEvent, Int>()

    fun has(event: StoryEvent) = !scenes[event].isNullOrEmpty()

    /** 사건에 맞는 문장 하나를 골라 이름을 채워 돌려준다. 문장이 없으면 null. */
    fun pick(event: StoryEvent): String? {
        val lines = scenes[event].orEmpty()
        if (lines.isEmpty()) return null
        val previous = lastIndex[event]
        val candidates = lines.indices.filter { lines.size == 1 || it != previous }
        val index = candidates[random.nextInt(candidates.size)]
        lastIndex[event] = index
        return fill(lines[index])
    }

    fun fill(line: String) = line.replace("{아이1}", child1).replace("{아이2}", child2)

    companion object {
        const val DEFAULT_CHILD_1 = "대장"
        const val DEFAULT_CHILD_2 = "부대장"
    }
}
