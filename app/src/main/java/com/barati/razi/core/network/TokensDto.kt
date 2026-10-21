package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** باشگاه مراجعان و ژتون (سرور: app/routers/tokens.py). */

@Serializable
data class TokenTxDto(
    val id: String,
    val amount: Int,
    val kind: String,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class RewardDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val cost: Int,
)

@Serializable
data class VoucherDto(
    val code: String,
    @SerialName("reward_title") val rewardTitle: String,
    val cost: Int,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class TokensDto(
    val balance: Int,
    @SerialName("referral_code") val referralCode: String,
    val referred: Boolean = false,
    @SerialName("referral_reward") val referralReward: Int = 0,
    @SerialName("referred_bonus") val referredBonus: Int = 0,
    val history: List<TokenTxDto> = emptyList(),
    val rewards: List<RewardDto> = emptyList(),
    val vouchers: List<VoucherDto> = emptyList(),
)

@Serializable
data class ReferralRequest(val code: String)

@Serializable
data class RedeemRequest(@SerialName("reward_id") val rewardId: String)
