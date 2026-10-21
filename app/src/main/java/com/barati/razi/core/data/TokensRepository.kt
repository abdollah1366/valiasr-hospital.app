package com.barati.razi.core.data

import com.barati.razi.core.network.PatientApiService
import com.barati.razi.core.network.RedeemRequest
import com.barati.razi.core.network.ReferralRequest
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.TokensDto
import com.barati.razi.core.network.VoucherDto
import com.barati.razi.core.network.apiCall

/** باشگاه مراجعان؛ موجودی و سابقه همیشه از سرور خوانده می‌شود. */
class TokensRepository(private val patientApi: PatientApiService) {
    suspend fun overview(): Result<TokensDto> = apiCall { patientApi.tokens() }

    suspend fun enterReferral(code: String): Result<TokensDto> = apiCall { patientApi.enterReferral(ReferralRequest(code.trim())) }

    suspend fun redeem(rewardId: String): Result<VoucherDto> = apiCall { patientApi.redeemReward(RedeemRequest(rewardId)) }
}
