package com.barati.razi

import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.RewardDto
import com.barati.razi.core.network.TokensDto
import com.barati.razi.feature.club.canAfford
import com.barati.razi.feature.club.tokenText
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesOverviewWithDefaults() {
        val t = json.decodeFromString<TokensDto>("""{"balance":70,"referral_code":"AB12CD"}""")
        assertEquals(70, t.balance)
        assertFalse(t.referred)
        assertTrue(t.rewards.isEmpty())
    }

    @Test
    fun rewardAffordableOnlyWithEnoughTokens() {
        val r = RewardDto("1", "تخفیف", null, 60)
        assertFalse(canAfford(59, r))
        assertTrue(canAfford(60, r))
    }

    @Test
    fun tokenTextFollowsLanguageDigits() {
        assertEquals("۱۲", tokenText(12, AppLanguage.FA))
        assertEquals("12", tokenText(12, AppLanguage.EN))
    }
}
