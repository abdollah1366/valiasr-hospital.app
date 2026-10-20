package com.barati.razi

import com.barati.razi.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorTest {
    @Test
    fun parsesServerDetail() {
        val e = ApiError.parse(
            422,
            """{"detail":{"code":"invalid_answers","message":"برخی پاسخ‌ها نامعتبرند","fields":{"description":"الزامی"}}}""",
        )
        assertEquals("invalid_answers", e.code)
        assertEquals("الزامی", e.fields["description"])
        assertFalse(e.isRetryable)
    }

    @Test
    fun fallsBackOnPlainDetailOrGarbage() {
        assertEquals("مورد درخواستی پیدا نشد", ApiError.parse(404, """{"detail":"Not Found"}""").message)
        assertTrue(ApiError.parse(502, "<html>bad gateway</html>").isRetryable)
    }
}
