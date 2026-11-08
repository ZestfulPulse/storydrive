package com.zestfulpulse.storydrive.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class SceneBookTest {
    @Test fun `같은 사건의 문장이 여러 개면 직전과 다른 것을 고른다`() {
        val book = SceneBook(mapOf(StoryEvent.IDLE to listOf("가", "나", "다")), random = Random(7))
        var previous = book.pick(StoryEvent.IDLE)
        repeat(30) {
            val next = book.pick(StoryEvent.IDLE)
            assertNotEquals(previous, next)
            previous = next
        }
    }

    @Test fun `문장이 하나면 그 문장을 반복한다`() {
        val book = SceneBook(mapOf(StoryEvent.GO to listOf("출발!")))
        assertEquals("출발!", book.pick(StoryEvent.GO))
        assertEquals("출발!", book.pick(StoryEvent.GO))
    }

    @Test fun `이름 자리를 기본값으로 채운다`() {
        val book = SceneBook(mapOf(StoryEvent.TURN_RIGHT to listOf("{아이1}, {아이2}, 꽉 잡아!")))
        assertEquals("대장, 부대장, 꽉 잡아!", book.pick(StoryEvent.TURN_RIGHT))
    }

    @Test fun `문장이 없는 사건은 null`() {
        assertNull(SceneBook(emptyMap()).pick(StoryEvent.WARM))
    }
}
