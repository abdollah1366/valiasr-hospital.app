package com.barati.razi

import com.barati.razi.core.network.ResultDto
import com.barati.razi.feature.results.filterResults
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesLabResultWithFlaggedItem() {
        val r = json.decodeFromString<ResultDto>(
            """{"id":"a","kind":"lab","title":"CBC","performed_at":"2026-10-01T08:00:00+03:30",
            "items":[{"name":"Hb","value":"11.9","unit":"g/dL","reference":"12 - 16","flag":"low"}]}""",
        )
        assertEquals("ready", r.status)
        assertEquals("low", r.items.single().flag)
    }

    @Test
    fun filtersByKind() {
        fun r(id: String, kind: String) = ResultDto(id, kind, id, "2026-10-01T08:00:00Z")
        val all = listOf(r("a", "lab"), r("b", "imaging"), r("c", "lab"))
        assertEquals(listOf("a", "c"), filterResults(all, "lab").map { it.id })
        assertEquals(3, filterResults(all, null).size)
    }
}
